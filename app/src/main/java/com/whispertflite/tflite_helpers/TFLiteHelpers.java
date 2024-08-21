// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.whispertflite.tflite_helpers;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.Pair;

import com.qualcomm.qti.QnnDelegate;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.GpuDelegateFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TFLiteHelpers {
    private static final String TAG = "QualcommTFLiteHelpers";

    public enum DelegateType {
        // GPUv2 Delegate: https://www.tensorflow.org/lite/performance/gpu
        // https://app.aihub.qualcomm.com/docs/hub/api.html#profile-inference-options
        // https://app.aihub.qualcomm.com/docs/hub/api.html#tflite-delegate-options-for-gpuv2
        //
        // Limitations:
        //   * Some settings for GPUv2 that AI Hub sets are not used in this helper file because
        //     they are not accessible via the TFLite Java API. Expect a slight difference
        //     in on-device performance compared to what AI Hub reports for the same model.
        //
        GPUv2,

        // QNN Delegate (NPU): https://docs.Qualcomm.com/bundle/publicresource/topics/80-63442-50/introduction.html
        // https://app.aihub.qualcomm.com/docs/hub/api.html#profile-inference-options
        // https://app.aihub.qualcomm.com/docs/hub/api.html#tflite-delegate-options-for-qnn
        //
        // Limitations:
        //   * Applicable only on Qualcomm chipsets that support the QNN SDK.
        //
        //   * Floating point compute is supported only on Snapdragon 8 Gen 1 and newer (with some exceptions).
        //     CreateInterpreterAndDelegatesFromOptions will not apply this delegate for floating point models on hardware that does not support them.
        //     See documentation for hardware support details https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/overview.html
        QNN_NPU,

        // ------
        //
        // Additional delegates (eg. NNAPI, or something targeting non-Qualcomm hardware) could be added here.
        //
        // ------
    }

    /**
     * Create a TFLite interpreter from the given model.
     *
     * @param tfLiteModel           The model to load.
     *
     * @param delegatePriorityOrder Delegates, in order they should be registered to the interpreter.
     *
     *                              The "inner array" defines which delegates should be registered when creating the interpreter.
     *                              The order of delegates is the priority in which they are assigned layers.
     *                              For example, if an array contains delegates { QNN_NPU, GPUv2 }, then QNN_NPU will be assigned any
     *                              compatible op first. GPUv2 will then be assigned any ops that QNN_NPU is unable to run.
     *                              And finally, XNNPack will be assigned ops that both QNN_NPU and GPUv2 are unable to run.
     *
     *                              The "outer array" defines the order of delegate list the interpreter should be created with.
     *                              This method will first attempt to create an interpreter using all delegates in the first array.
     *                              If that interpreter fails to instantiate, this method will try to create an interpreter
     *                              using all delegates in the second array. This continues until an interpreter could be successfully
     *                              created & returned, or until all arrays are tried unsuccessfully--which results in an exception.
     *
     * @param numCPUThreads         Number of CPU threads to use for layers on CPU.
     * @param nativeLibraryDir      Android.Context.nativeLibraryDir (native library directory location)
     * @param cacheDir              Android app cache directory.
     * @param modelIdentifier       Unique identifier string for the model being loaded.
     *
     * @return A pair of the created interpreter and associated delegates.
     *         These delegates must be kept in memory until they are no longer needed. Before
     *         deleting, the client must call close() on the returned delegates and interpreter.
     */
    public static Pair<Interpreter, Map<DelegateType, Delegate>> CreateInterpreterAndDelegatesFromOptions(
            MappedByteBuffer tfLiteModel,
            DelegateType[][] delegatePriorityOrder,
            int numCPUThreads,
            String nativeLibraryDir,
            String cacheDir,
            String modelIdentifier) {

        // Delegate Storage
        Map<DelegateType, Delegate> delegates = new HashMap<>();

        // All delegates we've tried to instantiate, whether that was successful or not.
        Set<DelegateType> attemptedDelegates = new HashSet<>();

        // Attempt to register delegate pairings in the defined priority order.
        for (DelegateType[] delegatesToRegister : delegatePriorityOrder) {
            // Create delegates for this attempt if we haven't done so already.
            Arrays.stream(delegatesToRegister)
                    .filter(delegateType -> !attemptedDelegates.contains(delegateType))
                    .forEach(delegateType -> {
                        Delegate delegate = CreateDelegate(delegateType, nativeLibraryDir, cacheDir, modelIdentifier);
                        if (delegate != null) {
                            delegates.put(delegateType, delegate);
                        }
                        attemptedDelegates.add(delegateType);
                    });

            // If one or more delegates in this attempt could not be instantiated,
            // skip this attempt.
            if (Arrays.stream(delegatesToRegister).anyMatch(x -> !delegates.containsKey(x))) {
                continue;
            }

            // Create interpreter.
            Interpreter interpreter = CreateInterpreterFromDelegates(
                Arrays.stream(delegatesToRegister).map(
                        delegateType -> new Pair<>(delegateType, delegates.get(delegateType))
                ).toArray(Pair[]::new),
                numCPUThreads,
                tfLiteModel
            );

            // If the interpreter failed to be created, move on to the next attempt.
            if (interpreter == null) {
                continue;
            }

            // Drop & close delegates that were not used by this attempt.
            delegates.keySet().stream()
                    .filter(delegateType -> Arrays.stream(delegatesToRegister).noneMatch(d -> d == delegateType))
                    .collect(Collectors.toSet()) // needed so we don't modify the same object we're looping over
                    .forEach(unusedDelegateType -> {
                        Objects.requireNonNull(delegates.remove(unusedDelegateType)).close();
                    });

            // Return interpreter & associated delegates.
            return new Pair<>(interpreter, delegates);
        }

        throw new RuntimeException("Unable to create an interpreter of any kind for the provided model. See log for details.");
    }

    /**
     * Create an interpreter from the given delegates.
     *
     * @param delegates     Delegate instances to be registered in the interpreter.
     *                      Delegates will be registered in the order of this array.
     * @param numCPUThreads Number of CPU threads to use for layers on CPU.
     * @param tfLiteModel   TFLiteModel to pass to the interpreter.
     * @return An Interpreter if creation is successful, and null otherwise.
     */
    public static Interpreter CreateInterpreterFromDelegates(
            final Pair<DelegateType, Delegate>[] delegates,
            int numCPUThreads,
            MappedByteBuffer tfLiteModel) {
        Interpreter.Options tfLiteOptions = new Interpreter.Options();
        tfLiteOptions.setRuntime(Interpreter.Options.TfLiteRuntime.FROM_APPLICATION_ONLY);
        tfLiteOptions.setAllowBufferHandleOutput(true);
        tfLiteOptions.setUseNNAPI(false);
        tfLiteOptions.setNumThreads(numCPUThreads);
        tfLiteOptions.setUseXNNPACK(true); // Fall back to XNNPack (fast CPU implementation) if a layer cannot run on NPU.

        // Register delegates in this interpreter. The first delegate
        // registered will have "first pick" of which operators to run, and so on.
        Arrays.stream(delegates).forEach(x -> tfLiteOptions.addDelegate(x.second));

        try {
            Interpreter i = new Interpreter(tfLiteModel, tfLiteOptions);
            i.allocateTensors();
            return i;
        } catch (Exception e) {
            List<String> enabledDelegates = Arrays.stream(delegates).map(x -> x.first.name()).collect(Collectors.toCollection(ArrayList<String>::new));
            enabledDelegates.add("XNNPack");
            Log.e(TAG, "Failed to Load Interpreter with delegates {" + String.join(", ", enabledDelegates) + "} | " + e.getMessage());
            return null;
        }
    }

    /**
     * Load a TF Lite model from disk.
     *
     * @param assets        Android app asset manager.
     * @param modelFilename File name of the resource to load.
     * @return The loaded model in MappedByteBuffer format, and a unique model identifier hash string.
     * @throws IOException If the model file does not exist or cannot be read.
     */
    public static Pair<MappedByteBuffer, String> loadModelFile(AssetManager assets, String modelFilename)
            throws IOException, NoSuchAlgorithmException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        MappedByteBuffer buffer;
        String hash;

        try (FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();

            // Map the file to a buffer
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            // Compute the hash
            MessageDigest hashDigest = MessageDigest.getInstance("MD5");
            inputStream.skip(startOffset);
            try (DigestInputStream dis = new DigestInputStream(inputStream, hashDigest)) {
                byte[] data = new byte[8192];
                int numRead = 0;
                while (numRead < declaredLength) {
                    numRead += dis.read(data, 0, Math.min(8192, (int)declaredLength - numRead));
                }; // Computing MD5 hash
            }

            // Convert hash to string
            StringBuilder hex = new StringBuilder();
            for (byte b : hashDigest.digest()) {
                hex.append(String.format("%02x", b));
            }
            hash = hex.toString();
        }

        return new Pair<>(buffer, hash);
    }

    /**
     * @param delegateType     The type of delegate to create.
     * @param nativeLibraryDir Native library directory for Android app.
     * @param cacheDir         Android app cache directory.
     * @param modelIdentifier  Unique identifier string for the model being loaded.
     * @return The created delegate if successful, and null otherwise.
     */
    static Delegate CreateDelegate(DelegateType delegateType, String nativeLibraryDir, String cacheDir, String modelIdentifier) {
        if (delegateType == DelegateType.GPUv2) {
            return CreateGPUv2Delegate(cacheDir, modelIdentifier);
        }
        if (delegateType == DelegateType.QNN_NPU) {
            return CreateQNN_NPUDelegate(nativeLibraryDir, cacheDir, modelIdentifier);
        }

        // ------
        //
        // Additional delegates (eg. NNAPI, or something targeting non-Qualcomm hardware) could be created here.
        //
        // ------

        throw new RuntimeException("Delegate creation not implemented for type: " + delegateType.name());
    }

    /**
     * Create and configure the QNN NPU delegate.
     * QNN NPU will be configured for maximum performance (at the cost of device battery life / heat / precision).
     *
     * @param nativeLibraryDir Native library directory for Android app.
     * @param cacheDir         Android app cache directory.
     * @param modelIdentifier  Unique identifier string for the model being loaded.
     * @return The created delegate if successful, and null otherwise.
     */
    static Delegate CreateQNN_NPUDelegate(String nativeLibraryDir, String cacheDir, String modelIdentifier) {
        QnnDelegate.Options qnnOptions = new QnnDelegate.Options();
        // Point the QNN Delegate to the QNN libraries to use.
        qnnOptions.setSkelLibraryDir(nativeLibraryDir);
        qnnOptions.setLogLevel(QnnDelegate.Options.LogLevel.LOG_LEVEL_WARN);

        // The QNN delegate will compile this model for use with the NPU.
        //
        // If the cache dir and model token are set, the compiled asset will be
        // stored on disk so the model does not need to be recompiled on each load.
        qnnOptions.setCacheDir(cacheDir);
        qnnOptions.setModelToken(modelIdentifier);

        // -------------------------------
        // TO REPLICATE AN AI HUB JOB...
        //
        // Replace the below if/else statement with the "Runtime Configuration" shown
        // in the profile job page.
        //
        // The "QNN Options" section applies here.
        //
        // backend_type --> qnnOptions.setBackendType(QnnDelegate.Options.BackendType.<TYPE>)
        // log_level --> qnnOptions.setLogLevel(QnnDelegate.Options.LogLevel.<LOG_LEVEL>);
        //
        // dsp_options.performance_mode --> qnnOptions.setDspOptions(QnnDelegate.Options.DspPerformanceMode.<PERF_MODE>,
        //                                                           QnnDelegate.Options.DspPdSession.DSP_PD_SESSION_ADAPTIVE);
        //
        // htp_options.performance_mode --> qnnOptions.setHtpPerformanceMode(QnnDelegate.Options.HtpPerformanceMode.<PERF_MODE>);
        // htp_options.precision --> qnnOptions.setHtpPrecision(QnnDelegate.Options.HtpPrecision.<PRECISION>);
        // htp_options.useConvHmx --> qnnOptions.setHtpUseConvHmx(QnnDelegate.Options.HtpUseConvHmx.HTP_CONV_HMX_<ON/OFF>);
        //
        // -------------------------------

        if (QnnDelegate.checkCapability(QnnDelegate.Capability.DSP_RUNTIME)) {
            qnnOptions.setBackendType(QnnDelegate.Options.BackendType.DSP_BACKEND);
            qnnOptions.setDspOptions(QnnDelegate.Options.DspPerformanceMode.DSP_PERFORMANCE_BURST, QnnDelegate.Options.DspPdSession.DSP_PD_SESSION_ADAPTIVE);
        } else {
            boolean hasHTP_FP16 = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_FP16);
            boolean hasHTP_QUANT = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_QUANTIZED);

            if (!hasHTP_FP16 && !hasHTP_QUANT) {
                Log.e(TAG, "QNN with NPU backend is not supported on this device.");
                return null;
            }

            qnnOptions.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND);
            qnnOptions.setHtpUseConvHmx(QnnDelegate.Options.HtpUseConvHmx.HTP_CONV_HMX_ON);
            qnnOptions.setHtpPerformanceMode(QnnDelegate.Options.HtpPerformanceMode.HTP_PERFORMANCE_BURST);

            if (hasHTP_FP16) {
                qnnOptions.setHtpPrecision(QnnDelegate.Options.HtpPrecision.HTP_PRECISION_FP16);
            }
        }

        try {
            return new QnnDelegate(qnnOptions);
        } catch (Exception e) {
            Log.e(TAG, "QNN with NPU backend failed to initialize: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create and configure the GPUv2 delegate.
     * GPUv2 will be configured for maximum performance (at the cost of device battery life / heat / precision),
     * and to allow execution in FP16 precision.
     *
     * @param cacheDir         Android app cache directory.
     * @param modelIdentifier  Unique identifier string for the model being loaded.
     * @return A The created delegate if successful, and null otherwise.
     */
    static Delegate CreateGPUv2Delegate(String cacheDir, String modelIdentifier) {
        GpuDelegateFactory.Options gpuOptions = new GpuDelegateFactory.Options();

        // -------------------------------
        // TO REPLICATE AN AI HUB JOB...
        //
        // Replace the gpuOptions settings with the "Runtime Configuration" shown
        // in the profile job page.
        //
        // The "GPUv2 Delegate Option" section applies here.
        //
        // inference_preference -->  gpuOptions.setInferencePreference(GpuDelegateFactory.Options.<PREFERENCE>)
        //
        // inference_priority1,2,3 --> The Java API for TFLite cannot access this setting directly.
        //                             A related setting exists that can act as an approximation:
        //
        //                             If TFLITE_GPU_INFERENCE_PRIORITY_MAX_PRECISION is priority 2 or 3, set
        //                                  gpuOptions.setPrecisionLossAllowed(true);
        //                             Otherwise, set
        //                                  gpuOptions.setPrecisionLossAllowed(false);
        //
        //
        // -------------------------------
        gpuOptions.setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED);
        gpuOptions.setPrecisionLossAllowed(true);
        gpuOptions.setSerializationParams(cacheDir, modelIdentifier);

        try {
            return new GpuDelegate(gpuOptions);
        } catch (Exception e) {
            Log.e(TAG, "GPUv2 delegate failed to initialize: " + e.getMessage());
            return null;
        }
    }
}
