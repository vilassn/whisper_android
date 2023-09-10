package com.whispertflite.kotlin;

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class WhisperUtil {

    var vocab = WhisperVocab()
    var filters = WhisperFilter()
    var mel = WhisperMel()

    // Helper class definitions
    class WhisperVocab {
        // Token types
        var tokenEot = 50256 // end of transcript
        var tokenSot = 50257 // start of transcript
        var tokenPrev = 50360
        var tokenSolm = 50361 // ??
        var tokenNot = 50362 // no timestamps
        var tokenBeg = 50363

        // Available tasks
        val tokenTranslate = 50358
        val tokenTranscribe = 50359

        var tokenToWord: MutableMap<Int, String> = HashMap()

        // Vocab types
        val nVocabMultilingual = 51865    // for multilingual vocab
        val nVocabNonMultilingual = 51864 // for non multilingual vocab

        // Initialise nVocab as default types
        var nVocab = nVocabMultilingual

        fun setMultilingual(multilingual: Boolean) {
            if (multilingual)
                nVocab = nVocabMultilingual
            else
                nVocab = nVocabNonMultilingual
        }

        fun isMultilingual(): Boolean {
            return nVocab == nVocabMultilingual
        }
    }

    class WhisperFilter {
        var nMel = 0
        var nFft = 0
        lateinit var data: FloatArray
    }

    class WhisperMel {
        var nLen = 0
        var nMel = 0
        lateinit var data: FloatArray
    }

    // Helper functions definitions
    fun getWordFromToken(token: Int): String? {
        return vocab.tokenToWord[token]
    }

    companion object {
        private val TAG = "WhisperUtil"

        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SIZE = 30
        const val WHISPER_MEL_LEN = 3000
        val golden_generated_ids = intArrayOf(
            50257, 50362, 1770, 13, 2264, 346, 353, 318,
            262, 46329, 286, 262, 3504, 6097, 11, 290, 356, 389, 9675, 284, 7062
        )

        private val threadCounter = AtomicInteger(0)

        // nSamples => WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE => 480000
        fun getMelSpectrogram(
                samples: FloatArray, nSamples: Int, sampleRate: Int,
                fftSize: Int, fftStep: Int, nMel: Int, nThreads: Int,
                filters: WhisperFilter, mel: WhisperMel
        ): Boolean {
            mel.nMel = nMel
            mel.nLen = nSamples / fftStep
            mel.data = FloatArray(mel.nMel * mel.nLen)
            val hann = FloatArray(fftSize)
            for (i in 0 until fftSize)
                hann[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()

            val nFft = 1 + fftSize / 2
            val workers = arrayOfNulls<Thread>(nThreads)
            for (iw in 0 until nThreads) {
                workers[iw] = Thread {
                    val ith = threadCounter.incrementAndGet()
                    // Log.d(TAG, "In getMelSpectrogram(), Thread: $ith")
                    val fftIn = FloatArray(fftSize)
                    for (i in 0 until fftSize)
                        fftIn[i] = 0.0f

                    val fftOut = FloatArray(fftSize * 2)
                    var i = ith
                    while (i < mel.nLen) {
                        val offset = i * fftStep

                        // apply Hanning window
                        for (j in 0 until fftSize) {
                            if (offset + j < nSamples)
                                fftIn[j] = hann[j] * samples[offset + j]
                            else
                                fftIn[j] = 0.0f
                        }

                        // FFT -> mag^2
                        fft(fftIn, fftOut)
                        for (j in 0 until fftSize)
                            fftOut[j] = fftOut[2 * j + 0] * fftOut[2 * j + 0] + fftOut[2 * j + 1] * fftOut[2 * j + 1]

                        for (j in 0 until fftSize / 2)
                            fftOut[j] += fftOut[fftSize - j - 1]

                        // mel spectrogram
                        for (j in 0 until mel.nMel) {
                            var sum = 0.0
                            for (k in 0 until nFft)
                                sum += (fftOut[k] * filters.data[j * nFft + k]).toDouble()

                            if (sum < 1e-10)
                                sum = 1e-10

                            sum = log10(sum)
                            mel.data[j * mel.nLen + i] = sum.toFloat()
                        }
                        i += nThreads
                    }
                }
                workers[iw]!!.start()
            }

            for (iw in 0 until nThreads) {
                try {
                    workers[iw]!!.join()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            // clamping and normalization
            var mmax = -1e20
            for (i in 0 until mel.nMel * mel.nLen) {
                if (mel.data[i] > mmax) {
                    mmax = mel.data[i].toDouble()
                }
            }

            mmax -= 8.0
            for (i in 0 until mel.nMel * mel.nLen) {
                if (mel.data[i] < mmax) {
                    mel.data[i] = mmax.toFloat()
                }
                mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
            }

            return true
        }

        private fun dft(input: FloatArray, output: FloatArray) {
            val inSize = input.size
            for (k in 0 until inSize) {
                var re = 0f
                var im = 0f
                for (n in 0 until inSize) {
                    val angle = 2 * Math.PI.toFloat() * k * n / inSize
                    re += (input[n] * cos(angle.toDouble())).toFloat()
                    im -= (input[n] * sin(angle.toDouble())).toFloat()
                }
                output[k * 2 + 0] = re
                output[k * 2 + 1] = im
            }
        }

        private fun fft(input: FloatArray, output: FloatArray) {
            val inSize = input.size
            if (inSize == 1) {
                output[0] = input[0]
                output[1] = 0f
                return
            }

            if (inSize % 2 == 1) {
                dft(input, output)
                return
            }

            val even = FloatArray(inSize / 2)
            val odd = FloatArray(inSize / 2)

            var indxEven = 0
            var indxOdd = 0
            for (i in 0 until inSize) {
                if (i % 2 == 0) {
                    even[indxEven] = input[i]
                    indxEven++
                } else {
                    odd[indxOdd] = input[i]
                    indxOdd++
                }
            }

            val evenFft = FloatArray(inSize)
            val oddFft = FloatArray(inSize)

            fft(even, evenFft)
            fft(odd, oddFft)
            for (k in 0 until inSize / 2) {
                val theta = 2 * Math.PI.toFloat() * k / inSize
                val re = cos(theta.toDouble()).toFloat()
                val im = -sin(theta.toDouble()).toFloat()
                val reOdd = oddFft[2 * k + 0]
                val imOdd = oddFft[2 * k + 1]
                output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
                output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
                output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
                output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
            }
        }
    }
}