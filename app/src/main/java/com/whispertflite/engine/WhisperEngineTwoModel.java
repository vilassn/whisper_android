package com.whispertflite.engine;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.whispertflite.asr.IWhisperListener;
import com.whispertflite.utils.WaveUtil;
import com.whispertflite.utils.WhisperUtil;
import com.whispertflite.tflite_helpers.TFLiteHelpers;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class WhisperEngineTwoModel implements IWhisperEngine {
    private static final String TAG = "WhisperEngineTransl";
    private final WhisperUtil mWhisperUtil = new WhisperUtil();

    private static final String SIGNATURE_KEY = "serving_default";
    private static final String WHISPER_ENCODER = "whisper-encoder-base.tflite";
    private static final String WHISPER_DECODER_LANGUAGE = "whisper-decoder-base.tflite";
    private static final String WHISPER_VOCAB_MULTILINGUAL = "filters_vocab_multilingual.bin";

    private boolean mIsInitialized = false;
    private IWhisperListener mUpdateListener = null;
    private Interpreter mInterpreterEncoder = null;
    private Interpreter mInterpreterDecoder = null;
    private final AtomicBoolean mIsInterrupted = new AtomicBoolean(false);

    private Context mContext;
    private Map<TFLiteHelpers.DelegateType, org.tensorflow.lite.Delegate> mEncoderDelegates;
    private Map<TFLiteHelpers.DelegateType, org.tensorflow.lite.Delegate> mDecoderDelegates;

    public WhisperEngineTwoModel(Context context) {
        mContext = context;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public void interrupt() {
        mIsInterrupted.set(true);
    }

    public void updateStatus(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdateReceived(message);
    }

    public void setUpdateListener(IWhisperListener listener) {
        mUpdateListener = listener;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        // Load model
        String filesDir = new File(modelPath).getParent();
        String encoderModelPath = new File(filesDir, WHISPER_ENCODER).getAbsolutePath();
        String decoderModelPath = new File(filesDir, WHISPER_DECODER_LANGUAGE).getAbsolutePath();

        try {
            // Load encoder model
            Pair<MappedByteBuffer, String> encoderModelPair = TFLiteHelpers.loadModelFile(mContext.getAssets(), WHISPER_ENCODER);
            MappedByteBuffer encoderModel = encoderModelPair.first;
            String encoderModelIdentifier = encoderModelPair.second;

            // Load decoder model
            Pair<MappedByteBuffer, String> decoderModelPair = TFLiteHelpers.loadModelFile(mContext.getAssets(), WHISPER_DECODER_LANGUAGE);
            MappedByteBuffer decoderModel = decoderModelPair.first;
            String decoderModelIdentifier = decoderModelPair.second;

            // Create interpreters with optimized delegates
            TFLiteHelpers.DelegateType[][] delegatePriorityOrder = {
                    {TFLiteHelpers.DelegateType.QNN_NPU, TFLiteHelpers.DelegateType.GPUv2},
                    {TFLiteHelpers.DelegateType.GPUv2},
                    {}  // CPU fallback
            };

            Pair<Interpreter, Map<TFLiteHelpers.DelegateType, org.tensorflow.lite.Delegate>> encoderPair = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
                    encoderModel,
                    delegatePriorityOrder,
                    Runtime.getRuntime().availableProcessors(),
                    mContext.getApplicationInfo().nativeLibraryDir,
                    mContext.getCacheDir().getAbsolutePath(),
                    encoderModelIdentifier
            );

            mInterpreterEncoder = encoderPair.first;
            mEncoderDelegates = encoderPair.second;

            Pair<Interpreter, Map<TFLiteHelpers.DelegateType, org.tensorflow.lite.Delegate>> decoderPair = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
                    decoderModel,
                    delegatePriorityOrder,
                    Runtime.getRuntime().availableProcessors(),
                    mContext.getApplicationInfo().nativeLibraryDir,
                    mContext.getCacheDir().getAbsolutePath(),
                    decoderModelIdentifier
            );

            mInterpreterDecoder = decoderPair.first;
            mDecoderDelegates = decoderPair.second;

        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            return false;
        }

        Log.d(TAG, "Models are loaded and optimized.");

        // Load filters and vocab
        String multilingualVocabPath = new File(filesDir, WHISPER_VOCAB_MULTILINGUAL).getAbsolutePath();
        boolean ret = mWhisperUtil.loadFiltersAndVocab(multilingual, multilingualVocabPath);
        if (ret) {
            mIsInitialized = true;
            Log.d(TAG, "Filters and Vocab are loaded..." + multilingualVocabPath);
        } else {
            mIsInitialized = false;
            Log.d(TAG, "Failed to load Filters and Vocab...");
        }

        return mIsInitialized;
    }


    @Override
    public String transcribeFile(String wavePath) {
        // Set interrupted false ast beginning
        mIsInterrupted.set(false);

        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...");
        float[] melSpectrogram = getMelSpectrogram(wavePath);
        Log.d(TAG, "Mel spectrogram is calculated...!");

        // Perform inference
        String result = runInference(melSpectrogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return null;
    }


    private float[] getMelSpectrogram(String wavePath) {
        // Get samples in PCM_FLOAT format
        float[] samples = WaveUtil.getSamples(wavePath);

        int fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        int copyLength = Math.min(samples.length, fixedInputSize);
        System.arraycopy(samples, 0, inputSamples, 0, copyLength);

        int cores = Runtime.getRuntime().availableProcessors();
        return mWhisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, cores);
    }

    private String runInference(float[] inputData) {
        ByteBuffer encoderOutput = runEncoder(inputData);

        // TODO: set input audio language and action as per need
        int inputLang = 50259; // English 50259, Spanish 50262, Hindi 50276
        return runDecoder(encoderOutput, inputLang, mWhisperUtil.getTokenTranscribe());
    }

    private ByteBuffer runEncoder(float[] inputBuffer) {
        // Load the TFLite model and allocate tensors for encoder
        mInterpreterEncoder.allocateTensors();

        // Prepare encoder input and output buffers as TensorBuffers
        Tensor inputTensor = mInterpreterEncoder.getInputTensor(0);
        TensorBuffer encoderInputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());

        Tensor outputTensor = mInterpreterEncoder.getOutputTensor(0);
        TensorBuffer encoderOutputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType());

        // Set encoder input data in encoderInputBuffer
        encoderInputBuffer.loadArray(inputBuffer);

        // Create the input and output maps for encoder
        Map<String, Object> encoderInputsMap = new HashMap<>();
        String[] encoderInputs = mInterpreterEncoder.getSignatureInputs(SIGNATURE_KEY);
        for (String str : encoderInputs)
            System.out.println("encoderInputs*****************" + str);
        encoderInputsMap.put(encoderInputs[0], encoderInputBuffer.getBuffer());

        Map<String, Object> encoderOutputsMap = new HashMap<>();
        String[] encoderOutputs = mInterpreterEncoder.getSignatureOutputs(SIGNATURE_KEY);
        for (String str : encoderOutputs)
            System.out.println("encoderOutputs*****************" + str);
        encoderOutputsMap.put(encoderOutputs[0], encoderOutputBuffer.getBuffer());

        // Run the encoder
        //mInterpreterEncoder.runForMultipleInputsOutputs(encoderInputsMap, encoderOutputsMap);
        mInterpreterEncoder.runSignature(encoderInputsMap, encoderOutputsMap, SIGNATURE_KEY);

        return encoderOutputBuffer.getBuffer();
    }

    private String runDecoder(ByteBuffer inputBuffer, long inputLang, long action) {
        // Initialize decoderInputIds to store the input ids for the decoder
        long[][] decoderInputIds = new long[1][384];

        // Create a prefix array with start of transcript, input language, action, and not time stamps
        long[] prefix = {mWhisperUtil.getTokenSOT(), inputLang, action, mWhisperUtil.getTokenNOT()};
        int prefixLen = prefix.length;

        // Copy prefix elements to decoderInputIds
        System.arraycopy(prefix, 0, decoderInputIds[0], 0, prefixLen);

        // Create a buffer to store the decoder's output
        float[][][] decoderOutputBuffer = new float[1][384][51865];

        // Load the TFLite model and allocate tensors for the decoder
        mInterpreterDecoder.allocateTensors();

        // Create input and output maps for the decoder
        Map<String, Object> decoderInputsMap = new HashMap<>();
        String[] decoderInputs = mInterpreterDecoder.getSignatureInputs(SIGNATURE_KEY);
        for (String str : decoderInputs)
            System.out.println("decoderInputs*****************" + str);
        decoderInputsMap.put(decoderInputs[0], inputBuffer);
        decoderInputsMap.put(decoderInputs[1], decoderInputIds);

        Map<String, Object> decoderOutputsMap = new HashMap<>();
        String[] decoderOutputs = mInterpreterDecoder.getSignatureOutputs(SIGNATURE_KEY);
        for (String str : decoderOutputs)
            System.out.println("decoderOutputs*****************" + str);
        decoderOutputsMap.put(decoderOutputs[0], decoderOutputBuffer);

        StringBuilder result = new StringBuilder();

        int nextToken = -1;
        while (nextToken != mWhisperUtil.getTokenEOT()) {
            // Resize decoder input for the next token
            mInterpreterDecoder.resizeInput(1, new int[]{1, prefixLen});

            // Run the decoder for the next token
            mInterpreterDecoder.runSignature(decoderInputsMap, decoderOutputsMap);

            // Process the output to get the next token
            nextToken = argmax(decoderOutputBuffer[0], prefixLen - 1);
            decoderInputIds[0][prefixLen] = nextToken;
            prefixLen += 1;

            if (nextToken != mWhisperUtil.getTokenEOT()) {
                String word = mWhisperUtil.getWordFromToken(nextToken);
                if (word != null) {
                    Log.i(TAG, "token: " + nextToken + ", word: " + word);
                    result.append(word);
                    updateStatus(result.toString());
                }
            }

            if(mIsInterrupted.get())
                break;
        }

        //debug(decoderOutputBuffer[0]);

        return result.toString();
    }

    private int argmax(float[][] decoderOutputBuffer, int index) {
        int maxIndex = 0;
        for (int j = 0; j < decoderOutputBuffer[index].length; j++) {
            //System.out.println("*******argmax: " + decoderOutputBuffer[index][j]);
            if (decoderOutputBuffer[index][j] > decoderOutputBuffer[index][maxIndex]) {
                maxIndex = j;
            }
        }

        return maxIndex;
    }

    private int debug(float[][] decoderOutputBuffer) {
        StringBuilder result = new StringBuilder();
        Log.d(TAG, "...decoderOutputBuffer.len: " + decoderOutputBuffer.length);
        Log.d(TAG, "...decoderOutputBuffer[0].len: " + decoderOutputBuffer[0].length);
        for (int i = 0; i < decoderOutputBuffer.length; i++) {
            int maxIndex = 0;
            for (int j = 0; j < decoderOutputBuffer[i].length; j++) {
                //System.out.println("decoderOutputBuffer[i][j]): " + i + " " + " " + j + " " + decoderOutputBuffer[i][j]);
                if (decoderOutputBuffer[i][j] > decoderOutputBuffer[i][maxIndex]) {
                    maxIndex = j;
                }
            }
            Log.d(TAG, "i: " + i + ", max: " + maxIndex );
            String word = mWhisperUtil.getWordFromToken(maxIndex);
            if (word != null) {
//                Log.i(TAG, "i: " + i + ", max: " + maxIndex + ", word: " + word);
                result.append(word);
            }
        }
        Log.i(TAG, "result: " + result);

        return 0;
    }
    //Cleanup method to close interpreters and delegates
    public void cleanup() {
        if (mInterpreterEncoder != null) {
            mInterpreterEncoder.close();
        }
        if (mInterpreterDecoder != null) {
            mInterpreterDecoder.close();
        }
        if (mEncoderDelegates != null) {
            for (org.tensorflow.lite.Delegate delegate : mEncoderDelegates.values()) {
                delegate.close();
            }
        }
        if (mDecoderDelegates != null) {
            for (org.tensorflow.lite.Delegate delegate : mDecoderDelegates.values()) {
                delegate.close();
            }
        }
    }
}
