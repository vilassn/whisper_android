package com.whispertflite.kotlin

import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Vector

class TFLiteEngineTwo {

    private val TAG = "TFLiteEngine"

    private var mIsInitialized = false
    private val mWhisper = WhisperUtil()

    private val SIGNATURE_KEY = "serving_default"
    private val ENCODER_INPUT_SHAPE = intArrayOf(1, 80, 3000)

//    private static final String WHISPER_ENCODER = "nnmodel/mine/whisper-encoder-hybrid.tflite";
//    private static final String WHISPER_DECODER_LANGUAGE = "nnmodel/mine/whisper-decoder-language-hybrid.tflite";

    private val WHISPER_ENCODER = "whisper-encoder-tiny.tflite"
    private val WHISPER_DECODER_LANGUAGE = "whisper-decoder-tiny.tflite"

    private var _encoder: Interpreter? = null
    private var _decoder: Interpreter? = null
    private var _dictionary: Dictionary? = null

    fun isInitialized(): Boolean {
        return mIsInitialized
    }

    @Throws(IOException::class)
    fun initialize(assets: AssetManager, multilingual: Boolean, vocabPath: String, modelPath: String): Boolean {

        // Load model
        loadModel(assets)
        Log.d(TAG, "Model is loaded...!$modelPath")

        // Load filters and vocab
        loadFiltersAndVocab(multilingual, vocabPath)
        Log.d(TAG, "Filters and Vocab are loaded...!")

        mIsInitialized = true

        return true
    }

    fun getTranscription(wavePath: String, inputLang: Long, action: Long): String? {
        val melSpectrogram: FloatArray = getMelSpectrogram(wavePath)
        val encoderOutput: ByteBuffer = runEncoder(melSpectrogram)
        return runDecoder(encoderOutput, inputLang, action)
    }

    @Throws(IOException::class)
    private fun loadModel(assets: AssetManager): Boolean {

        val options = Interpreter.Options()
        val flexDelegate = FlexDelegate()
//        GpuDelegate gpuDelegate = new GpuDelegate();
//        NnApiDelegate nnapiDelegate = new NnApiDelegate();

        options.addDelegate(flexDelegate)
//options.addDelegate(gpuDelegate);
//options.addDelegate(nnapiDelegate);

        options.numThreads = 8
        options.setUseXNNPACK(true)
        options.useNNAPI = false
        try {
            val whisper_encoder: ByteBuffer = loadWhisperModel(assets, WHISPER_ENCODER)
            val whisper_decoder_language:ByteBuffer = loadWhisperModel(assets, WHISPER_DECODER_LANGUAGE)
            _encoder = Interpreter(whisper_encoder, options)
            _decoder = Interpreter(whisper_decoder_language, options)
            val vocab = ExtractVocab.extractVocab(assets.open("filters_vocab_multilingual.bin"))
            val phraseMappings = HashMap<String, String>()
            _dictionary = Dictionary(vocab, phraseMappings)
            println("Engine is initialized.......!")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mIsInitialized = true

        return true
    }

    @Throws(IOException::class)
    private fun loadWhisperModel(assets: AssetManager, modelName: String): ByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Load filters and vocab data from pre-generated filters_vocab_gen.bin file
    @Throws(IOException::class)
    private fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String) {

        // Set whether vocab is multilingual or not
        mWhisper.vocab.setMultilingual(multilingual)

        // Read vocab file
        val bytes = Files.readAllBytes(Paths.get(vocabPath))
        val vocabBuf = ByteBuffer.wrap(bytes)
        vocabBuf.order(ByteOrder.nativeOrder())
        Log.d(TAG, "Vocab file size: " + vocabBuf.limit())

        // @magic:USEN
        val magic = vocabBuf.int
        if (magic == 0x5553454e) {
            Log.d(TAG, "Magic number: $magic")
        } else {
            Log.d(TAG, "Invalid vocab file (bad magic: $magic), $vocabPath")
            return
        }

        // Load mel filters
        mWhisper.filters.nMel = vocabBuf.int
        mWhisper.filters.nFft = vocabBuf.int
        Log.d(TAG, "nMel:" + mWhisper.filters.nMel + ", nFft:" + mWhisper.filters.nFft)
        val filterData = ByteArray(mWhisper.filters.nMel * mWhisper.filters.nFft * Float.SIZE_BYTES)
        vocabBuf[filterData, 0, filterData.size]
        val filterBuf = ByteBuffer.wrap(filterData)
        filterBuf.order(ByteOrder.nativeOrder())
        mWhisper.filters.data = FloatArray(mWhisper.filters.nMel * mWhisper.filters.nFft)
        var index = 0
        while (filterBuf.hasRemaining()) {
            mWhisper.filters.data[index] = filterBuf.float
            index++
        }

        // Load vocabulary
        val nVocab = vocabBuf.int // 50257
        Log.d(TAG, "nVocab: $nVocab")
        for (i in 0 until nVocab) {
            val len = vocabBuf.int
            val wordBytes = ByteArray(len)
            vocabBuf[wordBytes, 0, wordBytes.size]
            val word = String(wordBytes)
            mWhisper.vocab.tokenToWord[i] = word
        }

        // Add additional vocab ids
        if (mWhisper.vocab.isMultilingual()) {
            mWhisper.vocab.tokenEot++
            mWhisper.vocab.tokenSot++
            mWhisper.vocab.tokenPrev++
            mWhisper.vocab.tokenSolm++
            mWhisper.vocab.tokenNot++
            mWhisper.vocab.tokenBeg++
        }

        for (i in nVocab until mWhisper.vocab.nVocab) {
            var word: String
            if (i > mWhisper.vocab.tokenBeg) {
                word = "[_TT_" + (i - mWhisper.vocab.tokenBeg) + "]"
            } else if (i == mWhisper.vocab.tokenEot) {
                word = "[_EOT_]"
            } else if (i == mWhisper.vocab.tokenSot) {
                word = "[_SOT_]"
            } else if (i == mWhisper.vocab.tokenPrev) {
                word = "[_PREV_]"
            } else if (i == mWhisper.vocab.tokenNot) {
                word = "[_NOT_]"
            } else if (i == mWhisper.vocab.tokenBeg) {
                word = "[_BEG_]"
            } else {
                word = "[_extra_token_$i]"
            }

            mWhisper.vocab.tokenToWord[i] = word
            //Log.d(TAG, "i= $i, word= $word");
        }
    }

    private fun getMelSpectrogram(wavePath: String): FloatArray {
        val meanValues: FloatArray = WaveUtil.readAudioFile(wavePath)
        val samples = FloatArray(WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE)
        //System.arraycopy(originalArray, 0, largerArray, 0, originalArray.length);
        for (i in samples.indices) {
            if (i < meanValues.size)
                samples[i] = meanValues[i]
            else
                samples[i] = 0f
        }

        val cores = Runtime.getRuntime().availableProcessors()
        if (!WhisperUtil.getMelSpectrogram(samples, samples.size, WhisperUtil.WHISPER_SAMPLE_RATE,
                        WhisperUtil.WHISPER_N_FFT, WhisperUtil.WHISPER_HOP_LENGTH, WhisperUtil.WHISPER_N_MEL,
                        cores, mWhisper.filters, mWhisper.mel)) {
            Log.d(TAG, "%s: failed to compute mel spectrogram")
        }

        return mWhisper.mel.data
    }

    // Encoder function using TensorBuffer
    private fun runEncoder(inputBuffer: FloatArray): ByteBuffer {
        // Load the TFLite model and allocate tensors for encoder
        val encoderInterpreter = _encoder!! // Load the encoder TFLite model
        encoderInterpreter.allocateTensors()

        // Prepare encoder input and output buffers as TensorBuffers
        val inputTensor = encoderInterpreter.getInputTensor(0)
        val encoderInputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType())
        val outputTensor = encoderInterpreter.getOutputTensor(0)
        val encoderOutputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

        // Set encoder input data in encoderInputBuffer
        encoderInputBuffer.loadArray(inputBuffer)

        // Create the input and output maps for encoder
        val encoderInputsMap: MutableMap<String, Any> = java.util.HashMap()
        val encoderInputs = encoderInterpreter.getSignatureInputs(SIGNATURE_KEY)
        for (str in encoderInputs) println("encoderInputs*****************$str")
        encoderInputsMap[encoderInputs[0]] = encoderInputBuffer.buffer
        val encoderOutputsMap: MutableMap<String, Any> = java.util.HashMap()
        val encoderOutputs = encoderInterpreter.getSignatureOutputs(SIGNATURE_KEY)
        for (str in encoderOutputs) println("encoderOutputs*****************$str")
        encoderOutputsMap[encoderOutputs[0]] = encoderOutputBuffer.buffer

        // Run the encoder
        //encoderInterpreter.runForMultipleInputsOutputs(encoderInputsMap, encoderOutputsMap);
        encoderInterpreter.runSignature(encoderInputsMap, encoderOutputsMap, SIGNATURE_KEY)
        return encoderOutputBuffer.buffer
    }

    // Decoder function using TensorBuffer
    private fun runDecoder(inputBuffer: ByteBuffer, inputLang: Long, action: Long): String {
        // Initialize decoderInputIds to store the input ids for the decoder
        val decoderInputIds = Array(1) { LongArray(384) }

        // Create a prefix array with start of transcript, input language, action, and not time stamps
        val prefix = longArrayOf(_dictionary!!.startOfTranscript.toLong(), inputLang, action, _dictionary!!.notTimeStamps.toLong())
        var prefixLen = prefix.size

        // Copy prefix elements to decoderInputIds
        System.arraycopy(prefix, 0, decoderInputIds[0], 0, prefixLen)

        // Initialize tokenStream to store the token sequence during decoding
        val tokenStream = Vector<Long>(prefixLen)

        // Add prefix elements to tokenStream
        for (p in 0 until prefixLen) {
            tokenStream.add(prefix[p])
        }

        // Create a buffer to store the decoder's output
        val decoderOutputBuffer = Array(1) { Array(384) { FloatArray(51865) } }

        // Load the TFLite model and allocate tensors for the decoder
        val decoderInterpreter = _decoder!! // Load the decoder TFLite model
        decoderInterpreter.allocateTensors()

        // Create input and output maps for the decoder
        val decoderInputsMap: MutableMap<String, Any> = java.util.HashMap()
        val decoderInputs = decoderInterpreter.getSignatureInputs(SIGNATURE_KEY)
        for (str in decoderInputs)
            println("decoderInputs*****************$str")
        decoderInputsMap[decoderInputs[0]] = inputBuffer
        decoderInputsMap[decoderInputs[1]] = decoderInputIds

        val decoderOutputsMap: MutableMap<String, Any> = java.util.HashMap()
        val decoderOutputs = decoderInterpreter.getSignatureOutputs(SIGNATURE_KEY)
        for (str in decoderOutputs)
            println("decoderOutputs*****************$str")
        decoderOutputsMap[decoderOutputs[0]] = decoderOutputBuffer

        var nextToken = -1
        while (nextToken != _dictionary!!.endOfTranscript) {
            // Resize decoder input for the next token
            decoderInterpreter.resizeInput(1, intArrayOf(1, prefixLen))

            // Run the decoder for the next token
            decoderInterpreter.runSignature(decoderInputsMap, decoderOutputsMap, SIGNATURE_KEY)

            // Process the output to get the next token
            nextToken = argmax(decoderOutputBuffer[0], prefixLen - 1)
            println("nextToken*****************$nextToken")
            tokenStream.add(nextToken.toLong())
            decoderInputIds[0][prefixLen] = nextToken.toLong()
            prefixLen += 1
        }

        // Convert the tokenStream to the final decoded text using the dictionary
        val whisperOutput = _dictionary!!.tokensToString(tokenStream)

        // Inject tokens to get the complete transcribed text
        return _dictionary!!.injectTokens(whisperOutput)
    }

    private fun argmax(decoderOutputBuffer: Array<FloatArray>, index: Int): Int {
        var maxIndex = 0
        for (j in decoderOutputBuffer[index].indices) {
            //System.out.println("*******argmax: " + decoderOutputBuffer[index][j]);
            if (decoderOutputBuffer[index][j] > decoderOutputBuffer[index][maxIndex]) {
                maxIndex = j
            }
        }
        return maxIndex
    }

    private fun reshape(byteBuffer: FloatArray, inputShape: IntArray): Array<Array<FloatArray>> {
        val reshapedFloats = Array(inputShape[0]) { Array(inputShape[1]) { FloatArray(inputShape[2]) } }
        var index = 0
        for (k in 0 until inputShape[2]) {
            for (j in 0 until inputShape[1]) {
                for (i in 0 until inputShape[0]) {
                    reshapedFloats[i][j][k] = byteBuffer[index]
                    index++
                }
            }
        }
        return reshapedFloats
    }

    private fun printTensorDump(tensor: Tensor) {
        Log.d(TAG, "  shape.length: " + tensor.shape().size)
        for (i in tensor.shape().indices)
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i])
        Log.d(TAG, "  dataType: " + tensor.dataType())
        Log.d(TAG, "  name: " + tensor.name())
        Log.d(TAG, "  numBytes: " + tensor.numBytes())
        Log.d(TAG, "  index: " + tensor.index())
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions())
        Log.d(TAG, "  numElements: " + tensor.numElements())
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().size)
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().scale)
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().zeroPoint)
        Log.d(TAG, "==================================================================")
    }
}