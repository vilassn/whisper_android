package com.example.tfliteaudio;

import android.util.Log;

import com.jlibrosa.audio.JLibrosa;
import com.jlibrosa.audio.exception.FileFormatNotSupportedException;
import com.jlibrosa.audio.wavFile.WavFileException;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFLiteEngine {

    private static final String TAG = "TFLiteEngine";

    private Interpreter mInterpreter;
    private final WhisperUtil mWhisper = new WhisperUtil();

    public boolean initialize(String vocabPath, String modelPath) throws IOException {
        // Load model
        loadModel(modelPath);
        Log.d(TAG, "Model is loaded...!" + modelPath);

        // Load filters and vocab
        loadFiltersAndVocab(vocabPath);
        Log.d(TAG, "Filters and Vocab are loaded...!");

        return true;
    }

    public String getTranscription(String wavePath) throws FileFormatNotSupportedException, IOException, WavFileException {
        // Calculate Mel spectrogram
        float[] melSpectogram = getMelSpectogram(wavePath);
        Log.d(TAG, "Mel spectogram is calculated...!");

        // Perform inference
        String result = runInference(melSpectogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    // Load TFLite model
    private void loadModel(String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(modelPath);
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        MappedByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        mInterpreter = new Interpreter(tfliteModel);
    }

    // Load filters and vocab data from preg enerated filters_vocab_gen.bin file
    private void loadFiltersAndVocab(String vocabPath) throws IOException {
        // Read vocab file
        byte[] bytes = Files.readAllBytes(Paths.get(vocabPath));
        ByteBuffer vocab_buf = ByteBuffer.wrap(bytes);
        vocab_buf.order(ByteOrder.LITTLE_ENDIAN);
        Log.d(TAG, "Vocab size: " + vocab_buf.limit());

        // @magic:USEN
        int magic = vocab_buf.getInt();
        if (magic == 0x5553454e) {
            Log.d(TAG, "Magic number: " + magic);
        } else {
            Log.d(TAG, "Invalid vocab file (bad magic: " + magic + "), " + vocabPath);
            return;
        }

        // Load mel filters
        mWhisper.filters.n_mel = vocab_buf.getInt();
        mWhisper.filters.n_fft = vocab_buf.getInt();
        Log.d(TAG, "n_mel:" + mWhisper.filters.n_mel + ", n_fft:" + mWhisper.filters.n_fft);

        byte[] filter_data = new byte[mWhisper.filters.n_mel * mWhisper.filters.n_fft * Float.BYTES];
        vocab_buf.get(filter_data, 0, filter_data.length);
        ByteBuffer filter_buf = ByteBuffer.wrap(filter_data);
        filter_buf.order(ByteOrder.nativeOrder());

        mWhisper.filters.data = new float[mWhisper.filters.n_mel * mWhisper.filters.n_fft];
        for (int i = 0; filter_buf.hasRemaining(); i++) {
            mWhisper.filters.data[i] = filter_buf.getFloat();
        }

        // Load vocabulary
        int n_vocab = vocab_buf.getInt(); // 50257
        Log.d(TAG, "n_vocab: " + n_vocab);

        mWhisper.vocab.n_vocab = n_vocab;
        for (int i = 0; i < mWhisper.vocab.n_vocab; i++) {
            int len = vocab_buf.getInt();
            byte[] word_bytes = new byte[len];
            vocab_buf.get(word_bytes, 0, word_bytes.length);
            String word = new String(word_bytes);
            mWhisper.vocab.id_to_token.put(i, word);
            //Log.d(TAG, "i= " + i + ", len= " + len + ", g_vocab= " + word);
        }

        // Add additional vocab ids
        mWhisper.vocab.n_vocab = 51864;
        if (mWhisper.vocab.is_multilingual()) {
            mWhisper.vocab.token_eot++;
            mWhisper.vocab.token_sot++;
            mWhisper.vocab.token_prev++;
            mWhisper.vocab.token_solm++;
            mWhisper.vocab.token_not++;
            mWhisper.vocab.token_beg++;
        }

        for (int i = n_vocab; i < mWhisper.vocab.n_vocab; i++) {
            String word;
            if (i > mWhisper.vocab.token_beg) {
                word = "[_TT_" + (i - mWhisper.vocab.token_beg) + "]";
            } else if (i == mWhisper.vocab.token_eot) {
                word = "[_EOT_]";
            } else if (i == mWhisper.vocab.token_sot) {
                word = "[_SOT_]";
            } else if (i == mWhisper.vocab.token_prev) {
                word = "[_PREV_]";
            } else if (i == mWhisper.vocab.token_not) {
                word = "[_NOT_]";
            } else if (i == mWhisper.vocab.token_beg) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + i + "]";
            }

            mWhisper.vocab.id_to_token.put(i, word);
            //Log.d(TAG, "i= " + i + ", g_vocab= " + word);
        }
    }

    private float[] getMelSpectogram(String wavePath) throws IOException, FileFormatNotSupportedException, WavFileException {

        float[] meanValues;
        if (!wavePath.endsWith(MainActivity.RECORDED_FILE)) {
            JLibrosa jLibrosa = new JLibrosa();
            meanValues = jLibrosa.loadAndRead(wavePath, -1, -1);
            Log.d(TAG, "Number of samples in audio file (" + wavePath + "): " + meanValues.length);
        } else {
            meanValues = WaveUtil.readWaveFile(wavePath);
        }

        float[] samples = new float[WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE];
        for (int i = 0; i < samples.length; i++) {
            if (i < meanValues.length)
                samples[i] = meanValues[i];
            else
                samples[i] = 0;
        }

        if (!WhisperUtil.getMelSpectrogram(samples, samples.length, WhisperUtil.WHISPER_SAMPLE_RATE, WhisperUtil.WHISPER_N_FFT,
                WhisperUtil.WHISPER_HOP_LENGTH, WhisperUtil.WHISPER_N_MEL, 8, mWhisper.filters, mWhisper.mel)) {
            Log.d(TAG, "%s: failed to compute mel spectrogram");
            return null;
        }

        Log.d(TAG, "mWhisper.mel.n_mel: " + mWhisper.mel.n_mel + ", mWhisper.mel.n_len: " + mWhisper.mel.n_len);
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
        int input_size = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES;
        ByteBuffer input_buf = ByteBuffer.allocateDirect(input_size);
        input_buf.order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            input_buf.putFloat(input);
        }

        // To test mel data as a input directly
//        try {
//            byte[] bytes = Files.readAllBytes(Paths.get("/data/user/0/com.example.tfliteaudio/files/mel_spectrogram.bin"));
//            input_buf = ByteBuffer.wrap(bytes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        inputBuffer.loadBuffer(input_buf);

        // Run inference
        mInterpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

        // Retrieve the results
        int output_len = outputBuffer.getIntArray().length;
        Log.d(TAG, "output_len: " + output_len);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < output_len; i++) {
            int token = outputBuffer.getBuffer().getInt();
            if (token == mWhisper.vocab.token_eot)
                break;

            if ((token != 50257) && (token != 50362))
                result.append(mWhisper.getStringFromToken(token));
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
