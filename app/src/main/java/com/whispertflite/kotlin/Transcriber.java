package com.whispertflite.kotlin;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.flex.FlexDelegate;
//import org.tensorflow.lite.gpu.GpuDelegate;
//import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Transcriber {
    private static final String SIGNATURE_KEY = "serving_default";
    private static final int[] ENCODER_INPUT_SHAPE = new int[]{1, 80, 3000};

//    private static final String WHISPER_ENCODER = "nnmodel/mine/whisper-encoder-hybrid.tflite";
//    private static final String WHISPER_DECODER_LANGUAGE = "nnmodel/mine/whisper-decoder-language-hybrid.tflite";

    private static final String WHISPER_ENCODER = "whisper-encoder-tiny.tflite";
    private static final String WHISPER_DECODER_LANGUAGE = "whisper-decoder-tiny.tflite";

    private Interpreter _encoder;
    private Interpreter _decoder;
    private Dictionary _dictionary;

    public Transcriber(AssetManager assetManager) {

        Interpreter.Options options = new Interpreter.Options();
        FlexDelegate flexDelegate = new FlexDelegate();
//        GpuDelegate gpuDelegate = new GpuDelegate();
//        NnApiDelegate nnapiDelegate = new NnApiDelegate();

        options.addDelegate(flexDelegate);
//        options.addDelegate(gpuDelegate);
//        options.addDelegate(nnapiDelegate);

        options.setNumThreads(8);
        options.setUseXNNPACK(true);
        options.setUseNNAPI(false);

        try {
            MappedByteBuffer whisper_encoder = loadWhisperModel(assetManager, WHISPER_ENCODER);
            MappedByteBuffer whisper_decoder_language = loadWhisperModel(assetManager, WHISPER_DECODER_LANGUAGE);

            _encoder = new Interpreter(whisper_encoder, options);
            _decoder = new Interpreter(whisper_decoder_language, options);
            Vocab vocab = ExtractVocab.extractVocab(assetManager.open("filters_vocab_multilingual.bin"));
            HashMap<String, String> phraseMappings = new HashMap<>();
            _dictionary = new Dictionary(vocab, phraseMappings);

            System.out.println("Engine is initialized.......!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @NonNull
    public String transcribeAudio(float[] inputBuffer, long inputLang, long action) {
        ByteBuffer encoderOutput = runEncoder(inputBuffer);
        return runDecoder(encoderOutput, inputLang, action);
    }

    // Encoder function using TensorBuffer
    private ByteBuffer runEncoder(float[] inputBuffer) {
        // Load the TFLite model and allocate tensors for encoder
        Interpreter encoderInterpreter = _encoder; // Load the encoder TFLite model
        encoderInterpreter.allocateTensors();

        // Prepare encoder input and output buffers as TensorBuffers
        Tensor inputTensor = encoderInterpreter.getInputTensor(0);
        TensorBuffer encoderInputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());

        Tensor outputTensor = encoderInterpreter.getOutputTensor(0);
        TensorBuffer encoderOutputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType());

        // Set encoder input data in encoderInputBuffer
        encoderInputBuffer.loadArray(inputBuffer);

        // Create the input and output maps for encoder
        Map<String, Object> encoderInputsMap = new HashMap<>();
        String[] encoderInputs = encoderInterpreter.getSignatureInputs(SIGNATURE_KEY);
        for (String str : encoderInputs)
            System.out.println("encoderInputs*****************" + str);
        encoderInputsMap.put(encoderInputs[0], encoderInputBuffer.getBuffer());

        Map<String, Object> encoderOutputsMap = new HashMap<>();
        String[] encoderOutputs = encoderInterpreter.getSignatureOutputs(SIGNATURE_KEY);
        for (String str : encoderOutputs)
            System.out.println("encoderOutputs*****************" + str);
        encoderOutputsMap.put(encoderOutputs[0], encoderOutputBuffer.getBuffer());

        // Run the encoder
        //encoderInterpreter.runForMultipleInputsOutputs(encoderInputsMap, encoderOutputsMap);
        encoderInterpreter.runSignature(encoderInputsMap, encoderOutputsMap, SIGNATURE_KEY);

        return encoderOutputBuffer.getBuffer();
    }

    // Decoder function using TensorBuffer
    private String runDecoder(ByteBuffer inputBuffer, long inputLang, long action) {
        // Initialize decoderInputIds to store the input ids for the decoder
        long[][] decoderInputIds = new long[1][384];

        // Create a prefix array with start of transcript, input language, action, and not time stamps
        long[] prefix = {_dictionary.getStartOfTranscript(), inputLang, action, _dictionary.getNotTimeStamps()};
        int prefixLen = prefix.length;

        // Copy prefix elements to decoderInputIds
        System.arraycopy(prefix, 0, decoderInputIds[0], 0, prefixLen);

        // Initialize tokenStream to store the token sequence during decoding
        Vector<Long> tokenStream = new Vector<>(prefixLen);

        // Add prefix elements to tokenStream
        for (int p = 0; p < prefixLen; p++) {
            tokenStream.add(prefix[p]);
        }

        // Create a buffer to store the decoder's output
        float[][][] decoderOutputBuffer = new float[1][384][51865];

        // Load the TFLite model and allocate tensors for the decoder
        Interpreter decoderInterpreter = _decoder; // Load the decoder TFLite model
        decoderInterpreter.allocateTensors();

        // Create input and output maps for the decoder
        Map<String, Object> decoderInputsMap = new HashMap<>();
        String[] decoderInputs = decoderInterpreter.getSignatureInputs(SIGNATURE_KEY);
        for (String str : decoderInputs)
            System.out.println("decoderInputs*****************" + str);
        decoderInputsMap.put(decoderInputs[0], inputBuffer);
        decoderInputsMap.put(decoderInputs[1], decoderInputIds);

        Map<String, Object> decoderOutputsMap = new HashMap<>();
        String[] decoderOutputs = decoderInterpreter.getSignatureOutputs(SIGNATURE_KEY);
        for (String str : decoderOutputs)
            System.out.println("decoderOutputs*****************" + str);
        decoderOutputsMap.put(decoderOutputs[0], decoderOutputBuffer);

        int nextToken = -1;
        while (nextToken != _dictionary.getEndOfTranscript()) {
            // Resize decoder input for the next token
            decoderInterpreter.resizeInput(1, new int[]{1, prefixLen});

            // Run the decoder for the next token
            decoderInterpreter.runSignature(decoderInputsMap, decoderOutputsMap, SIGNATURE_KEY);

            // Process the output to get the next token
            nextToken = argmax(decoderOutputBuffer[0], prefixLen - 1);
            System.out.println("nextToken*****************" + nextToken);
            tokenStream.add((long) nextToken);
            decoderInputIds[0][prefixLen] = nextToken;

            prefixLen += 1;
        }

        // Convert the tokenStream to the final decoded text using the dictionary
        String whisperOutput = _dictionary.tokensToString(tokenStream);

        // Inject tokens to get the complete transcribed text
        return _dictionary.injectTokens(whisperOutput);
    }

    private int argmax(float[] array, int start) {
        int maxIndex = start;
        float maxValue = array[start];
        for (int i = start + 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
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

    private static MappedByteBuffer loadWhisperModel(AssetManager assets, String modelName)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @NonNull
    private float[][][] reshape(float[] byteBuffer, int[] inputShape) {
        float[][][] reshapedFloats = new float[inputShape[0]][inputShape[1]][inputShape[2]];
        int index = 0;
        for (int k = 0; k < inputShape[2]; k++) {
            for (int j = 0; j < inputShape[1]; j++) {
                for (int i = 0; i < inputShape[0]; i++) {
                    reshapedFloats[i][j][k] = byteBuffer[index];
                    index++;
                }
            }
        }
        return reshapedFloats;
    }
}