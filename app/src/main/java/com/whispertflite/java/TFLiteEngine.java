package com.whispertflite.java;

import android.util.Log;

import com.whispertflite.common.ITFLiteEngine;
import com.whispertflite.common.WaveUtil;
import com.whispertflite.common.WhisperUtil;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFLiteEngine implements ITFLiteEngine {
    private final String TAG = "TFLiteEngine";
    private boolean mIsInitialized = false;
    private final WhisperUtil mWhisper = new WhisperUtil();
    private Interpreter mInterpreter;

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(boolean multilingual, String vocabPath, String modelPath) throws IOException {
        // Load model
        loadModel(modelPath);
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
    private void loadModel(String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(modelPath);
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        ByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        // Set the number of threads for inference
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Runtime.getRuntime().availableProcessors());

        mInterpreter = new Interpreter(tfliteModel, options);
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
        // Create input tensor
        Tensor inputTensor = mInterpreter.getInputTensor(0);
        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());
        Log.d(TAG, "Input Tensor Dump ===>");
        printTensorDump(inputTensor);

        // Create output tensor
        Tensor outputTensor = mInterpreter.getOutputTensor(0);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32);
        Log.d(TAG, "Output Tensor Dump ===>");
        printTensorDump(outputTensor);

        // Load input data
        int inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(inputSize);
        inputBuf.order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            inputBuf.putFloat(input);
        }

        // To test mel data as a input directly
//        try {
//            byte[] bytes = Files.readAllBytes(Paths.get("/data/user/0/com.example.tfliteaudio/files/mel_spectrogram.bin"));
//            inputBuf = ByteBuffer.wrap(bytes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        inputBuffer.loadBuffer(inputBuf);

        // Run inference
        mInterpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

        // Retrieve the results
        int outputLen = outputBuffer.getIntArray().length;
        Log.d(TAG, "output_len: " + outputLen);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < outputLen; i++) {
            int token = outputBuffer.getBuffer().getInt();
            if (token == WhisperUtil.TOKEN_EOT)
                break;

            // Get word for token and Skip additional token
            if (token < WhisperUtil.TOKEN_EOT) {
                String word = mWhisper.getWordFromToken(token);
                Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word);
            } else {
                if (token == WhisperUtil.TASK_TRANSCRIBE)
                    Log.d(TAG, "It is Transcription...");

                if (token == WhisperUtil.TASK_TRANSLATE)
                    Log.d(TAG, "It is Translation...");

                String word = mWhisper.getWordFromToken(token);
                Log.d(TAG, "Skipping token: " + token + ", word: " + word);
            }
        }

        return result.toString();
    }

    private void printTensorDump(Tensor tensor) {
        Log.d(TAG, "  shape.length: " + tensor.shape().length);
        for (int i = 0; i < tensor.shape().length; i++)
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i]);
        Log.d(TAG, "  dataType: " + tensor.dataType());
        Log.d(TAG, "  name: " + tensor.name());
        Log.d(TAG, "  numBytes: " + tensor.numBytes());
        Log.d(TAG, "  index: " + tensor.index());
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions());
        Log.d(TAG, "  numElements: " + tensor.numElements());
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().length);
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().getScale());
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().getZeroPoint());
        Log.d(TAG, "==================================================================");
    }
}
