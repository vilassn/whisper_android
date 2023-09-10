package com.whispertflite.kotlin;

import android.media.AudioFormat
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.file.Files
import java.nio.file.Paths

object WaveUtil {

    private const val TAG = "WaveUtil"

    private const val MONO: Short = 1
    private const val STEREO: Short = 2
    private const val WAV_HEADER_SIZE = 44

    const val SAMPLE_RATE = 16000
    const val BYTES_PER_SAMPLE = 4
    const val NO_OF_CHANNELS = 1
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // should be as per NO_OF_CHANNELS
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT // should be as per BYTES_PER_SAMPLE
    const val BUFFER_SIZE_1_SEC = SAMPLE_RATE * NO_OF_CHANNELS * BYTES_PER_SAMPLE
    const val BUFFER_SIZE_30_SEC = BUFFER_SIZE_1_SEC * 30

    // wav header Endianness and number of bytes in value
    private var type = intArrayOf(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1)
    private var nBytes = intArrayOf(4, 4, 4, 4, 4, 2, 2, 4, 4, 2, 2, 4, 4)

    private fun convertToNumber(bytes: ByteArray, numOfBytes: Int, type: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(numOfBytes)
        if (type == 0) buffer.order(BIG_ENDIAN) else buffer.order(LITTLE_ENDIAN)
        buffer.put(bytes)
        buffer.rewind()
        return buffer
    }

    private fun convertToFloat(bytes: ByteArray): Float {
        var num = 0.0f
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.nativeOrder())
        if (bytes.size == 2)
            num = buffer.short.toFloat() / 32768.0f
        else if (bytes.size == 4)
            num = buffer.float

        return num
    }

    private fun reverseShortBytes(number: Short): Short {
        var num: Short = 0
        num = (num.toInt() or ((number.toInt() and 0xFF) shl 8)).toShort() // Reverse the first byte
        num = (num.toInt() or ((number.toInt() and 0xFF00) ushr 8)).toShort() // Reverse the second byte
        return num
    }

    private fun reverseIntBytes(number: Int): Int {
        var num = 0
        num = num or ((number and 0xFF) shl 24)   // Reverse the first byte
        num = num or ((number and 0xFF00) shl 8)  // Reverse the second byte
        num = num or ((number and 0xFF0000) ushr 8)  // Reverse the third byte
        num = num or ((number and 0xFF000000.toInt()) ushr 24)  // Reverse the fourth byte
        return num
    }

    fun getSamples(bytes: ByteArray, sampleSize: Int): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.nativeOrder())
        val samples = FloatArray(bytes.size / sampleSize)
        var i = 0
        while (buffer.remaining() >= sampleSize) {
            samples[i] = buffer.float
            i++
        }

        return samples
    }

    @Throws(IOException::class)
    fun createWaveFile(wavePath: String?, samples: FloatArray) {
        // get audio params
        val sampleRate = SAMPLE_RATE
        val channels = NO_OF_CHANNELS.toShort()
        val bytesPerSample = BYTES_PER_SAMPLE.toShort()

        // calculate header values
        val audioSize = samples.size * BYTES_PER_SAMPLE
        val blockAlign = (bytesPerSample * channels).toShort()
        val byteRate = sampleRate * bytesPerSample * channels
        val fileSize = audioSize + WAV_HEADER_SIZE
        val duration = audioSize / byteRate
        Log.d(TAG, "duration: $duration, audioSize: $audioSize")

        // The stream that writes the audio file to the disk
        val out = DataOutputStream(FileOutputStream(wavePath))

        // Write Header
        out.writeBytes("RIFF")
        out.writeInt(reverseIntBytes(fileSize - 8)) // Placeholder for file size
        out.writeBytes("WAVE")
        out.writeBytes("fmt ")
        out.writeInt(reverseIntBytes(16)) // Subchunk1Size
        out.writeShort(reverseShortBytes(3).toInt()) // AudioFormat PCM_16 = 1, PCM_FLOAT = 3
        out.writeShort(reverseShortBytes(channels).toInt()) // NumChannels
        out.writeInt(reverseIntBytes(sampleRate)) // SampleRate
        out.writeInt(reverseIntBytes(byteRate))  // ByteRate
        out.writeShort(reverseShortBytes(blockAlign).toInt()) // BlockAlign
        out.writeShort(reverseShortBytes((bytesPerSample * 8).toShort()).toInt()) // BitsPerSample
        out.writeBytes("data") // Subchunk2 ID always data
        out.writeInt(reverseIntBytes(audioSize)) // Placeholder for data size

        // Append audio data
        val buffer = ByteBuffer.allocate(audioSize)
        buffer.order(ByteOrder.nativeOrder())
        for (sample in samples)
            buffer.putFloat(sample)
        out.write(buffer.array())

        // close the stream properly
        out.close()
    }

    @Throws(IOException::class)
    fun readWaveFile(wavePath: String?): FloatArray {
        val bytes = Files.readAllBytes(Paths.get(wavePath))
        val buffer = ByteBuffer.wrap(bytes, WAV_HEADER_SIZE, bytes.size - WAV_HEADER_SIZE)
        buffer.order(ByteOrder.nativeOrder())

        val dataVector = ArrayList<Float>()
        while (buffer.remaining() >= BYTES_PER_SAMPLE)
            dataVector.add(buffer.float)

        return dataVector.toFloatArray()
    }


    @Throws(IOException::class)
    fun readAudioFile(audioFile: String): FloatArray {
        try {
            val file = File(audioFile)
            val length = file.length().toInt()
            println("length: $length")

            var numChannels: Short = 0
            var bitsPerSample: Short = 0
            val inputStream: InputStream = FileInputStream(file)
            for (index in nBytes.indices) {
                val byteArray = ByteArray(nBytes.get(index))
                var r = inputStream.read(byteArray, 0, nBytes.get(index))
                var byteBuffer = convertToNumber(byteArray, nBytes.get(index), type.get(index))
                if (index == 0) {
                    val chunkID = String(byteArray)
                    println("chunkID: $chunkID")
                } else if (index == 1) {
                    val chunkSize = byteBuffer.int
                    println("chunkSize: $chunkSize")
                } else if (index == 2) {
                    val format = String(byteArray)
                    println("format: $format")
                } else if (index == 3) {
                    val subChunk1ID = String(byteArray)
                    println("subChunk1ID: $subChunk1ID")
                } else if (index == 4) {
                    val subChunk1Size = byteBuffer.int
                    println("subChunk1Size: $subChunk1Size")
                } else if (index == 5) {
                    val audioFomart = byteBuffer.short
                    println("audioFomart: $audioFomart")
                } else if (index == 6) {
                    numChannels = byteBuffer.short
                    println("numChannels: $numChannels")
                } else if (index == 7) {
                    val sampleRate = byteBuffer.int
                    println("sampleRate: $sampleRate")
                } else if (index == 8) {
                    val byteRate = byteBuffer.int
                    println("byteRate: $byteRate")
                } else if (index == 9) {
                    val blockAlign = byteBuffer.short
                    println("blockAlign: $blockAlign")
                } else if (index == 10) {
                    bitsPerSample = byteBuffer.short
                    println("bitsPerSample: $bitsPerSample")
                } else if (index == 11) {
                    var subChunk2ID = String(byteArray)
                    if (subChunk2ID.compareTo("data") == 0) {
                        continue
                    } else if (subChunk2ID.compareTo("LIST") == 0) {
                        val byteArray2 = ByteArray(4)
                        r = inputStream.read(byteArray2, 0, 4)
                        byteBuffer = convertToNumber(byteArray2, 4, 1)
                        val temp = byteBuffer.int
                        //redundant data reading
                        val byteArray3 = ByteArray(temp)
                        r = inputStream.read(byteArray3, 0, temp)
                        r = inputStream.read(byteArray2, 0, 4)
                        subChunk2ID = String(byteArray2)
                    }
                } else if (index == 12) {
                    val subChunk2Size = byteBuffer.int
                    println("subChunk2Size: $subChunk2Size")
                }
            }

            val samples = ArrayList<Float>()
            val bytePerSample = bitsPerSample / 8
            val buffer = ByteArray(bytePerSample)

            while (true) {
                var ret = 0
                var sample = 0.0f
                if (numChannels == MONO) {
                    ret = inputStream.read(buffer, 0, bytePerSample)
                    sample = convertToFloat(buffer)
                } else if (numChannels == STEREO) {
                    ret = inputStream.read(buffer, 0, bytePerSample)
                    val sampleLeft = convertToFloat(buffer)
                    ret = inputStream.read(buffer, 0, bytePerSample)
                    val sampleRight = convertToFloat(buffer)
                    sample = (sampleLeft + sampleRight) / 2.0f
                }

                samples.add(sample)
                if (ret == -1)
                    break
            }
            return samples.toFloatArray()
        } catch (e: Exception) {
            println("Error: $e")
            return FloatArray(1)
        }
    }
}