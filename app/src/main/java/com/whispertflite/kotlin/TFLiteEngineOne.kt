package com.whispertflite.kotlin

import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths

class TFLiteEngineOne {

    private val TAG = "TFLiteEngine"
    private var mIsInitialized = false
    private val mWhisper = WhisperUtil()
    private var mInterpreter: Interpreter? = null

    fun isInitialized(): Boolean {
        return mIsInitialized
    }

    @Throws(IOException::class)
    fun initialize(multilingual: Boolean, vocabPath: String, modelPath: String): Boolean {

        // Load model
        loadModel(modelPath)
        Log.d(TAG, "Model is loaded...!$modelPath")

        // Load filters and vocab
        loadFiltersAndVocab(multilingual, vocabPath)
        Log.d(TAG, "Filters and Vocab are loaded...!")

        mIsInitialized = true

        return true
    }

    @Throws(IOException::class)
    fun getTranscription(wavePath: String): String {

        // Calculate Mel spectrogram
        val melSpectrogram = getMelSpectrogram(wavePath)
        Log.d(TAG, "Mel spectrogram is calculated...!")

        // Perform inference
        val result = runInference(melSpectrogram)
        Log.d(TAG, "Inference is executed...!")

        return result
    }

    // Load TFLite model
    @Throws(IOException::class)
    private fun loadModel(modelPath: String) {
        val fileInputStream = FileInputStream(modelPath)
        val fileChannel = fileInputStream.channel
        val startOffset: Long = 0
        val declaredLength = fileChannel.size()
        val tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        // Set the number of threads for inference
        val options = Interpreter.Options()
        options.numThreads = Runtime.getRuntime().availableProcessors();

        mInterpreter = Interpreter(tfliteModel, options)
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

    private fun getMelSpectrogram(wavePath: String): FloatArray? {
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
            return null
        }

        return mWhisper.mel.data
    }

    private fun runInference1(inputData: FloatArray?): String {

        // Create input tensor
        val inTensor = mInterpreter!!.getInputTensor(0)
        val inputBuffer = TensorBuffer.createFixedSize(inTensor.shape(), inTensor.dataType())
        //Log.d(TAG, "Input Tensor Dump ===>")
        //printTensorDump(inTensor)

        // Create output tensor
        val outTensor = mInterpreter!!.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outTensor.shape(), DataType.FLOAT32)
        //Log.d(TAG, "Output Tensor Dump ===>")
        //printTensorDump(outTensor)

        // Load input data
        val inputSize = inTensor.shape()[0] * inTensor.shape()[1] * inTensor.shape()[2] * Float.SIZE_BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData!!)
            inputBuf.putFloat(input)
        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        mInterpreter!!.run(inputBuffer.buffer, outputBuffer.buffer)

        // Retrieve the results
        val outputLen = outputBuffer.intArray.size
        Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.int
            if (token == mWhisper.vocab.tokenEot)
                break

            // Get word for token and Skip additional token
            if (token < mWhisper.vocab.tokenEot) {
                val word = mWhisper.getWordFromToken(token)
                Log.d(TAG, "Adding token: $token, word: $word")
                result.append(word)
            } else {
                if (token == mWhisper.vocab.tokenTranscribe)
                    Log.d(TAG, "It is Transcription...")

                if (token == mWhisper.vocab.tokenTranslate)
                    Log.d(TAG, "It is Translation...")

                val word = mWhisper.getWordFromToken(token)
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }

        return result.toString()
    }

    private fun runInference(inputData: FloatArray?): String {
        val SIGNATURE_KEY = "serving_default"

        // Create input tensor
        val inTensor = mInterpreter!!.getInputTensor(0)
        val inputBuffer = TensorBuffer.createFixedSize(inTensor.shape(), inTensor.dataType())
        //Log.d(TAG, "Input Tensor Dump ===>")
        //printTensorDump(inTensor)

        // Create output tensor
        val outTensor = mInterpreter!!.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outTensor.shape(), DataType.FLOAT32)
        //Log.d(TAG, "Output Tensor Dump ===>")
        //printTensorDump(outTensor)

        // Load input data
        val inputSize = inTensor.shape()[0] * inTensor.shape()[1] * inTensor.shape()[2] * Float.SIZE_BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData!!)
            inputBuf.putFloat(input)
        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        //mInterpreter!!.run(inputBuffer.buffer, outputBuffer.buffer)

        // Initialize decoderInputIds to store the input ids for the decoder
        val decoderInputIds = Array(1) { LongArray(384) }

        // Create a prefix array with start of transcript, input language, action, and not time stamps
        val prefix = longArrayOf(50257, 50262, 50358, 50362)
        val prefixLen = prefix.size

        // Copy prefix elements to decoderInputIds
        System.arraycopy(prefix, 0, decoderInputIds[0], 0, prefixLen)


        val signatures = mInterpreter!!.signatureKeys
        for(sig in signatures)
            println("signatures*****************$sig")

        // Create input and output maps for the decoder
        val decoderInputsMap: MutableMap<String, Any> = HashMap()
        val decoderInputs: Array<String> = mInterpreter!!.getSignatureInputs(SIGNATURE_KEY)
        for (str in decoderInputs)
            println("decoderInputs*****************$str")
        decoderInputsMap[decoderInputs[0]] = inputBuffer.buffer
        //decoderInputsMap[decoderInputs[1]] = decoderInputIds

        val decoderOutputsMap: MutableMap<String, Any> = HashMap()
        val decoderOutputs: Array<String> = mInterpreter!!.getSignatureOutputs(SIGNATURE_KEY)
        for (str in decoderOutputs)
            println("decoderOutputs*****************$str")
        decoderOutputsMap[decoderOutputs[0]] = outputBuffer.buffer

        // Run the encoder
        //mInterpreter!!.runForMultipleInputsOutputs(decoderInputsMap, decoderInputsMap);
        mInterpreter!!.runSignature(decoderInputsMap, decoderOutputsMap, SIGNATURE_KEY)

        // Retrieve the results
        val outputLen = outputBuffer.intArray.size
        Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.int
            if (token == mWhisper.vocab.tokenEot)
                break

            // Get word for token and Skip additional token
            if (token < mWhisper.vocab.tokenEot) {
                val word = mWhisper.getWordFromToken(token)
                Log.d(TAG, "Adding token: $token, word: $word")
                result.append(word)
            } else {
                if (token == mWhisper.vocab.tokenTranscribe)
                    Log.d(TAG, "It is Transcription...")

                if (token == mWhisper.vocab.tokenTranslate)
                    Log.d(TAG, "It is Translation...")

                val word = mWhisper.getWordFromToken(token)
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }

        return result.toString()
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