package com.example.tfliteaudio;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WhisperUtil {

    public static final int WHISPER_SAMPLE_RATE = 16000;
    public static final int WHISPER_N_FFT = 400;
    public static final int WHISPER_N_MEL = 80;
    public static final int WHISPER_HOP_LENGTH = 160;
    public static final int WHISPER_CHUNK_SIZE = 30;
    public static final int WHISPER_MEL_LEN = 3000;
    public static final int[] golden_generated_ids = {50257, 50362, 1770, 13, 2264, 346, 353, 318,
            262, 46329, 286, 262, 3504, 6097, 11, 290, 356, 389, 9675, 284, 7062};

    WhisperVocab vocab = new WhisperVocab();
    WhisperFilter filters = new WhisperFilter();
    WhisperMel mel = new WhisperMel();

    // Helper class definitions
    static class WhisperVocab {
        int n_vocab = 51864;
        int token_eot = 50256;
        int token_sot = 50257;
        int token_prev = 50360;
        int token_solm = 50361;
        int token_not = 50362;
        int token_beg = 50363;
        int token_translwordate = 50358;
        int token_transcribe = 50359;

        public Map<Integer, String> id_to_token = new HashMap<>();

        boolean is_multilingual() {
            return n_vocab == 51865;
        }
    }

    static class WhisperFilter {
        int n_mel;
        int n_fft;
        float[] data;
    }

    static class WhisperMel {
        int n_len;
        int n_mel;
        float[] data;
    }

    // Helper functions definitions
    public String getStringFromToken(int token) {
        return vocab.id_to_token.get(token);
    }

    private static final AtomicInteger thread_counter = new AtomicInteger(0);

    // n_samples => WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE => 480000
    public static boolean getMelSpectrogram(float[] samples, int n_samples, int sample_rate,
                                            int fft_size, int fft_step, int n_mel, int n_threads,
                                            WhisperFilter filters, WhisperMel mel) {
        mel.n_mel = n_mel;
        mel.n_len = n_samples / fft_step;
        mel.data = new float[mel.n_mel * mel.n_len];

        float[] hann = new float[fft_size];
        for (int i = 0; i < fft_size; i++)
            hann[i] = (float) (0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (fft_size))));

        final int n_fft = 1 + fft_size / 2;

        Thread[] workers = new Thread[n_threads];
        for (int iw = 0; iw < n_threads; ++iw) {
            workers[iw] = new Thread(() -> {
                int ith = thread_counter.incrementAndGet();
                Log.d("ASR", "=====> In getLogMelSpectrogram(), Thread: " + ith);

                float[] fft_in = new float[fft_size];
                for (int i = 0; i < fft_size; i++)
                    fft_in[i] = 0.0f;

                float[] fft_out = new float[fft_size * 2];
                for (int i = ith; i < mel.n_len; i += n_threads) {
                    final int offset = i * fft_step;

                    // apply Hanning window
                    for (int j = 0; j < fft_size; j++) {
                        if (offset + j < n_samples)
                            fft_in[j] = hann[j] * samples[offset + j];
                        else
                            fft_in[j] = 0.0F;
                    }

                    // FFT -> mag^2
                    fft(fft_in, fft_out);

                    for (int j = 0; j < fft_size; j++)
                        fft_out[j] = fft_out[2 * j + 0] * fft_out[2 * j + 0] + fft_out[2 * j + 1] * fft_out[2 * j + 1];

                    for (int j = 1; j < fft_size / 2; j++)
                        fft_out[j] += fft_out[fft_size - j];

                    // mel spectrogram
                    for (int j = 0; j < mel.n_mel; j++) {
                        double sum = 0.0;

                        for (int k = 0; k < n_fft; k++)
                            sum += fft_out[k] * filters.data[j * n_fft + k];

                        if (sum < 1e-10)
                            sum = 1e-10;

                        sum = Math.log10(sum);
                        mel.data[j * mel.n_len + i] = (float) sum;
                    }
                }
            });
            workers[iw].start();
        }

        for (int iw = 0; iw < n_threads; ++iw) {
            try {
                workers[iw].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // clamping and normalization
        double mmax = -1e20;
        for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
            if (mel.data[i] > mmax) {
                mmax = mel.data[i];
            }
        }

        //Log.d("ASR", "=====> In getLogMelSpectrogram(), mmax: " + String.format("%f", mmax));

        mmax -= 8.0;
        for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
            if (mel.data[i] < mmax) {
                mel.data[i] = (float) mmax;
            }

            mel.data[i] = (float) ((mel.data[i] + 4.0) / 4.0);
        }

        return true;
    }

    private static void dft(final float[] in, float[] out) {
        int N = in.length;
        for (int k = 0; k < N; k++) {
            float re = 0;
            float im = 0;

            for (int n = 0; n < N; n++) {
                float angle = (2 * (float) Math.PI * k * n) / N;
                re += in[n] * Math.cos(angle);
                im -= in[n] * Math.sin(angle);
            }

            out[k * 2 + 0] = re;
            out[k * 2 + 1] = im;
        }
    }

    private static void fft(final float[] in, float[] out) {
        int N = in.length;
        if (N == 1) {
            out[0] = in[0];
            out[1] = 0f;
            return;
        }

        if (N % 2 == 1) {
            dft(in, out);
            return;
        }

        float[] even = new float[N / 2];
        float[] odd = new float[N / 2];

        int indx_even = 0, indx_odd = 0;
        for (int i = 0; i < N; i++) {
            if (i % 2 == 0) {
                even[indx_even] = in[i];
                indx_even++;
            } else {
                odd[indx_odd] = in[i];
                indx_odd++;
            }
        }

        float[] even_fft = new float[N];
        float[] odd_fft = new float[N];

        fft(even, even_fft);
        fft(odd, odd_fft);

        for (int k = 0; k < N / 2; k++) {
            float theta = (2 * (float) Math.PI * k) / N;

            float re = (float) Math.cos(theta);
            float im = (float) -Math.sin(theta);

            float re_odd = odd_fft[2 * k + 0];
            float im_odd = odd_fft[2 * k + 1];

            out[2 * k + 0] = even_fft[2 * k + 0] + re * re_odd - im * im_odd;
            out[2 * k + 1] = even_fft[2 * k + 1] + re * im_odd + im * re_odd;

            out[2 * (k + N / 2) + 0] = even_fft[2 * k + 0] - re * re_odd + im * im_odd;
            out[2 * (k + N / 2) + 1] = even_fft[2 * k + 1] - re * im_odd - im * re_odd;
        }
    }
}