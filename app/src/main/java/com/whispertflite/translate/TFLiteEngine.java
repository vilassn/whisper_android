package com.whispertflite.translate;

import android.util.Log;

import com.whispertflite.common.ITFLiteEngine;
import com.whispertflite.common.WaveUtil;
import com.whispertflite.common.WhisperUtil;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.flex.FlexDelegate;
//import org.tensorflow.lite.gpu.GpuDelegate;
//import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TFLiteEngine implements ITFLiteEngine {
    private final String TAG = "TFLiteEngine";

    private static final String SIGNATURE_KEY = "serving_default";
    private static final String WHISPER_ENCODER = "whisper-encoder-tiny.tflite";
    private static final String WHISPER_DECODER_LANGUAGE = "whisper-decoder-tiny.tflite";

    private boolean mIsInitialized = false;
    private final WhisperUtil mWhisper = new WhisperUtil();
    private Interpreter mInterpreterEncoder;
    private Interpreter mInterpreterDecoder;

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(boolean multilingual, String vocabPath, String modelPath) throws IOException {
        // Load model
        String filesDir = new File(modelPath).getParent();
        mInterpreterEncoder = loadModel(new File(filesDir, WHISPER_ENCODER).getAbsolutePath());
        mInterpreterDecoder = loadModel(new File(filesDir, WHISPER_DECODER_LANGUAGE).getAbsolutePath());
        Log.d(TAG, "Model is loaded...!" + modelPath);

        // Load filters and vocab
        loadFiltersAndVocab(multilingual, vocabPath);
        Log.d(TAG, "Filters and Vocab are loaded...!");

        mIsInitialized = true;

        return true;
    }

    @Override
    public String getTranscription(String wavePath) {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...");
        float[] melSpectrogram = getMelSpectrogram(wavePath);
        Log.d(TAG, "Mel spectrogram is calculated...!");

        // Perform inference
        String result = runInference(melSpectrogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    // Load TFLite model
    private Interpreter loadModel(String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(modelPath);
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        ByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        // Set the number of threads for inference
        Interpreter.Options options = new Interpreter.Options();
        FlexDelegate flexDelegate = new FlexDelegate();
        options.addDelegate(flexDelegate);
        options.setNumThreads(Runtime.getRuntime().availableProcessors());
        options.setUseXNNPACK(true);
        options.setUseNNAPI(false);

        return new Interpreter(tfliteModel, options);
    }

    // Load filters and vocab data from pre-generated filters_vocab_gen.bin file
    private void loadFiltersAndVocab(boolean multilingual, String vocabPath) throws IOException {

        // Read vocab file
        byte[] bytes = Files.readAllBytes(Paths.get(vocabPath));
        ByteBuffer vocabBuf = ByteBuffer.wrap(bytes);
        vocabBuf.order(ByteOrder.nativeOrder());
        Log.d(TAG, "Vocab file size: " + vocabBuf.limit());

        // @magic:USEN
        int magic = vocabBuf.getInt();
        if (magic == 0x5553454e) {
            Log.d(TAG, "Magic number: " + magic);
        } else {
            Log.d(TAG, "Invalid vocab file (bad magic: " + magic + "), " + vocabPath);
            return;
        }

        // Load mel filters
        mWhisper.filters.nMel = vocabBuf.getInt();
        mWhisper.filters.nFft = vocabBuf.getInt();
        Log.d(TAG, "n_mel:" + mWhisper.filters.nMel + ", n_fft:" + mWhisper.filters.nFft);

        byte[] filterData = new byte[mWhisper.filters.nMel * mWhisper.filters.nFft * Float.BYTES];
        vocabBuf.get(filterData, 0, filterData.length);
        ByteBuffer filterBuf = ByteBuffer.wrap(filterData);
        filterBuf.order(ByteOrder.nativeOrder());

        mWhisper.filters.data = new float[mWhisper.filters.nMel * mWhisper.filters.nFft];
        for (int i = 0; filterBuf.hasRemaining(); i++) {
            mWhisper.filters.data[i] = filterBuf.getFloat();
        }

        // Load vocabulary
        int nVocab = vocabBuf.getInt();
        Log.d(TAG, "nVocab: " + nVocab);
        for (int i = 0; i < nVocab; i++) {
            int len = vocabBuf.getInt();
            byte[] wordBytes = new byte[len];
            vocabBuf.get(wordBytes, 0, wordBytes.length);
            String word = new String(wordBytes);
            mWhisper.vocab.tokenToWord.put(i, word);
        }

        // Add additional vocab ids
        int mVocabAdditional;
        if (!multilingual) {
            mVocabAdditional = WhisperUtil.N_VOCAB_ENGLISH;
        } else {
            mVocabAdditional = WhisperUtil.N_VOCAB_MULTILINGUAL;
            WhisperUtil.TOKEN_EOT++;
            WhisperUtil.TOKEN_SOT++;
            WhisperUtil.TOKEN_PREV++;
            WhisperUtil.TOKEN_SOLM++;
            WhisperUtil.TOKEN_NOT++;
            WhisperUtil.TOKEN_BEG++;
        }

        for (int i = nVocab; i < mVocabAdditional; i++) {
            String word;
            if (i > WhisperUtil.TOKEN_BEG) {
                word = "[_TT_" + (i - WhisperUtil.TOKEN_BEG) + "]";
            } else if (i == WhisperUtil.TOKEN_EOT) {
                word = "[_EOT_]";
            } else if (i == WhisperUtil.TOKEN_SOT) {
                word = "[_SOT_]";
            } else if (i == WhisperUtil.TOKEN_PREV) {
                word = "[_PREV_]";
            } else if (i == WhisperUtil.TOKEN_NOT) {
                word = "[_NOT_]";
            } else if (i == WhisperUtil.TOKEN_BEG) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + i + "]";
            }

            mWhisper.vocab.tokenToWord.put(i, word);
            //Log.d(TAG, "i= " + i + ", word= " + word);
        }
    }

    private float[] getMelSpectrogram(String wavePath) {
        // Get samples in PCM_FLOAT format
        float[] samples = WaveUtil.getSamples(wavePath);

        int fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        int copyLength = Math.min(samples.length, fixedInputSize);
        System.arraycopy(samples, 0, inputSamples, 0, copyLength);

        int cores = Runtime.getRuntime().availableProcessors();
        if (!WhisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, WhisperUtil.WHISPER_SAMPLE_RATE,
                WhisperUtil.WHISPER_N_FFT, WhisperUtil.WHISPER_HOP_LENGTH, WhisperUtil.WHISPER_N_MEL,
                cores, mWhisper.filters, mWhisper.mel)) {
            Log.d(TAG, "%s: failed to compute mel spectrogram");
            return null;
        }

        return mWhisper.mel.data;
    }

    private String runInference(float[] inputData) {
        ByteBuffer encoderOutput = runEncoder(inputData);
        // Input speech language (English 50259, Spanish 50262, Hindi 50276)
        return runDecoder(encoderOutput, 50262, WhisperUtil.TASK_TRANSLATE);
    }

    // Encoder function using TensorBuffer
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

    // Decoder function using TensorBuffer
    private String runDecoder(ByteBuffer inputBuffer, long inputLang, long action) {
        // Initialize decoderInputIds to store the input ids for the decoder
        long[][] decoderInputIds = new long[1][384];

        // Create a prefix array with start of transcript, input language, action, and not time stamps
        long[] prefix = {WhisperUtil.TOKEN_SOT, inputLang, action, WhisperUtil.TOKEN_NOT};
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
        while (nextToken != WhisperUtil.TOKEN_EOT) {
            // Resize decoder input for the next token
            mInterpreterDecoder.resizeInput(1, new int[]{1, prefixLen});

            // Run the decoder for the next token
            mInterpreterDecoder.runSignature(decoderInputsMap, decoderOutputsMap, SIGNATURE_KEY);

            // Process the output to get the next token
            nextToken = argmax(decoderOutputBuffer[0], prefixLen - 1);
            decoderInputIds[0][prefixLen] = nextToken;
            prefixLen += 1;

            if (nextToken != WhisperUtil.TOKEN_EOT) {
                String word = mWhisper.getWordFromToken(nextToken);
                if (word != null) {
                    Log.i(TAG, "token: " + nextToken + ", word: " + word);
                    result.append(word);
                }
            }
        }

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
}