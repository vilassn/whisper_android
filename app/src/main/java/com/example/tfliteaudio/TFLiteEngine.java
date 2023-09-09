package com.example.tfliteaudio;

import android.util.Log;

import com.jlibrosa.audio.JLibrosa;

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

public class TFLiteEngine {

    private final String TAG = "TFLiteEngine";
    private boolean mIsInitialized = false;
    private final WhisperUtil mWhisper = new WhisperUtil();
    private Interpreter mInterpreter;

    public boolean isInitialized() {
        return mIsInitialized;
    }

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

    public String getTranscription(String wavePath) {

        // Calculate Mel spectrogram
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

        // Set whether vocab is multilingual or not
        mWhisper.vocab.setMultilingual(multilingual);

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
        if (mWhisper.vocab.isMultilingual()) {
            mWhisper.vocab.tokenEot++;
            mWhisper.vocab.tokenSot++;
            mWhisper.vocab.tokenPrev++;
            mWhisper.vocab.tokenSolm++;
            mWhisper.vocab.tokenNot++;
            mWhisper.vocab.tokenBeg++;
        }

        for (int i = nVocab; i < mWhisper.vocab.nVocab; i++) {
            String word;
            if (i > mWhisper.vocab.tokenBeg) {
                word = "[_TT_" + (i - mWhisper.vocab.tokenBeg) + "]";
            } else if (i == mWhisper.vocab.tokenEot) {
                word = "[_EOT_]";
            } else if (i == mWhisper.vocab.tokenSot) {
                word = "[_SOT_]";
            } else if (i == mWhisper.vocab.tokenPrev) {
                word = "[_PREV_]";
            } else if (i == mWhisper.vocab.tokenNot) {
                word = "[_NOT_]";
            } else if (i == mWhisper.vocab.tokenBeg) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + i + "]";
            }

            mWhisper.vocab.tokenToWord.put(i, word);
            //Log.d(TAG, "i= " + i + ", word= " + word);
        }
    }

    private float[] getMelSpectrogram(String wavePath) {
        float[] meanValues = new float[0];
        try {
            if (wavePath.endsWith(WaveUtil.RECORDING_FILE)) {
//                JLibrosa jLibrosa = new JLibrosa();
//                meanValues = jLibrosa.loadAndRead(wavePath, -1, -1);
//                Log.d(TAG, "Number of samples in audio file (" + wavePath + "): " + meanValues.length);
                meanValues = WaveUtil.readWaveFile(wavePath);
                Log.d(TAG, "using WaveUtil.readWaveFile");
            } else {
                meanValues = WaveUtil.readAudioFile(wavePath);
                Log.d(TAG, "using WaveUtil.readAudioFile");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        float[] samples = new float[WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE];
        //System.arraycopy(originalArray, 0, largerArray, 0, originalArray.length);
        for (int i = 0; i < samples.length; i++) {
            if (i < meanValues.length)
                samples[i] = meanValues[i];
            else
                samples[i] = 0.0f;
        }

        int cores = Runtime.getRuntime().availableProcessors();
        if (!WhisperUtil.getMelSpectrogram(samples, samples.length, WhisperUtil.WHISPER_SAMPLE_RATE,
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
//            input_buf = ByteBuffer.wrap(bytes);
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
            if (token == mWhisper.vocab.tokenEot)
                break;

            // Get word for token and Skip additional token
            if (token < mWhisper.vocab.tokenEot) {
                String word = mWhisper.getWordFromToken(token);
                Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word);
            } else {
                if (token == mWhisper.vocab.tokenTranscribe)
                    Log.d(TAG, "It is Transcription...");

                if (token == mWhisper.vocab.tokenTranslate)
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
