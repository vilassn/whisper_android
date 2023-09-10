//Courtesy from @ggerganov https://github.com/ggerganov/whisper.cpp
#include <iostream>
#include <fstream>
#include <thread>
#include <sys/time.h>

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

int golden_generated_ids[21] = {50257,50362,1770,13,2264,346,353,318,262,46329,286,262,3504,6097,11,290,356,389,9675,284,7062};

struct whisper_vocab {
    using id    = int32_t;
    using token = std::string;

    int n_vocab = 51864;


    std::map<id, token> id_to_token;

    id token_eot  = 50256;
    id token_sot  = 50257;
    id token_prev = 50360;
    id token_solm = 50361; // ??
    id token_not  = 50362; // no timestamps
    id token_beg  = 50363;

    // available tasks
    static const id token_translwordate  = 50358;
    static const id token_transcribe = 50359;

    bool is_multilingual() const {
        return n_vocab == 51865;
    }
};

//whisper vocab global variable
whisper_vocab g_vocab;

//Added audio front end processing from https://github.com/ggerganov/whisper.cpp
// third-party utilities
// use your favorite implementations
#define WHISPER_SAMPLE_RATE 16000
#define WHISPER_N_FFT       400
#define WHISPER_N_MEL       80
#define WHISPER_HOP_LENGTH  160
#define WHISPER_CHUNK_SIZE  30
#define WHISPER_MEL_LEN     3000

struct whisper_filters {
    int32_t n_mel;
    int32_t n_fft;

    std::vector<float> data;
};

struct whisper_mel {
    int n_len;
    int n_mel;

    std::vector<float> data;
};

void print(std::vector <float> const &a) {
   std::cout << "The vector elements are : ";

   for(int i=0; i < 10; i++)
   std::cout << a.at(i) << ' ';
}

const char * whisper_token_to_str(int token) {
    return g_vocab.id_to_token.at(token).c_str();
}

// naive Discrete Fourier Transform
// input is real-valued
// output is complex-valued
void dft(const std::vector<float> & in, std::vector<float> & out) {
    int N = in.size();

    out.resize(N*2);

    for (int k = 0; k < N; k++) {
        float re = 0;
        float im = 0;

        for (int n = 0; n < N; n++) {
            float angle = 2*M_PI*k*n/N;
            re += in[n]*cos(angle);
            im -= in[n]*sin(angle);
        }

        out[k*2 + 0] = re;
        out[k*2 + 1] = im;
    }
}

// Cooley-Tukey FFT
// poor man's implementation - use something better
// input is real-valued
// output is complex-valued
void fft(const std::vector<float> & in, std::vector<float> & out) {
    out.resize(in.size()*2);

    int N = in.size();

    if (N == 1) {
        out[0] = in[0];
        out[1] = 0;
        return;
    }

    if (N%2 == 1) {
        dft(in, out);
        return;
    }

    std::vector<float> even;
    std::vector<float> odd;

    for (int i = 0; i < N; i++) {
        if (i % 2 == 0) {
            even.push_back(in[i]);
        } else {
            odd.push_back(in[i]);
        }
    }

    std::vector<float> even_fft;
    std::vector<float> odd_fft;

    fft(even, even_fft);
    fft(odd, odd_fft);

    for (int k = 0; k < N/2; k++) {
        float theta = 2*M_PI*k/N;

        float re = cos(theta);
        float im = -sin(theta);

        float re_odd = odd_fft[2*k + 0];
        float im_odd = odd_fft[2*k + 1];

        out[2*k + 0] = even_fft[2*k + 0] + re*re_odd - im*im_odd;
        out[2*k + 1] = even_fft[2*k + 1] + re*im_odd + im*re_odd;

        out[2*(k + N/2) + 0] = even_fft[2*k + 0] - re*re_odd + im*im_odd;
        out[2*(k + N/2) + 1] = even_fft[2*k + 1] - re*im_odd - im*re_odd;
    }
}

// ref: https://github.com/openai/whisper/blob/main/whisper/audio.py#L92-L124
bool log_mel_spectrogram(
    const float * samples,
    const int n_samples,
    const int sample_rate,
    const int fft_size,
    const int fft_step,
    const int n_mel,
    const int n_threads,
    const whisper_filters & filters,
    whisper_mel & mel) {

    // Hanning window
    std::vector<float> hann;
    hann.resize(fft_size);
    for (int i = 0; i < fft_size; i++) {
        hann[i] = 0.5*(1.0 - cos((2.0*M_PI*i)/(fft_size)));
    }

    mel.n_mel = n_mel;
    mel.n_len = (n_samples)/fft_step;
    mel.data.resize(mel.n_mel*mel.n_len);

    const int n_fft = 1 + fft_size/2;

    //printf("%s: n_samples = %d, n_len = %d\n", __func__, n_samples, mel.n_len);
    //printf("%s: recording length: %f s\n", __func__, (float) n_samples/sample_rate);

    std::vector<std::thread> workers(n_threads);
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            std::vector<float> fft_in;
            fft_in.resize(fft_size);
            for (int i = 0; i < fft_size; i++) {
                fft_in[i] = 0.0;
            }

            std::vector<float> fft_out;
            fft_out.resize(2*fft_size);

            for (int i = ith; i < mel.n_len; i += n_threads) {
                const int offset = i*fft_step;

                // apply Hanning window
                for (int j = 0; j < fft_size; j++) {
                    if (offset + j < n_samples) {
                        fft_in[j] = hann[j]*samples[offset + j];
                    } else {
                        fft_in[j] = 0.0;
                    }
                }

                // FFT -> mag^2
                fft(fft_in, fft_out);

                for (int j = 0; j < fft_size; j++) {
                    fft_out[j] = (fft_out[2*j + 0]*fft_out[2*j + 0] + fft_out[2*j + 1]*fft_out[2*j + 1]);
                }
                for (int j = 1; j < fft_size/2; j++) {
                    //if (i == 0) {
                    //    printf("%d: %f %f\n", j, fft_out[j], fft_out[fft_size - j]);
                    //}
                    fft_out[j] += fft_out[fft_size - j];
                }
                if (i == 0) {
                    //for (int j = 0; j < fft_size; j++) {
                    //    printf("%d: %e\n", j, fft_out[j]);
                    //}
                }

                // mel spectrogram
                for (int j = 0; j < mel.n_mel; j++) {
                    double sum = 0.0;

                    for (int k = 0; k < n_fft; k++) {
                        sum += fft_out[k]*filters.data[j*n_fft + k];
                    }
                    if (sum < 1e-10) {
                        sum = 1e-10;
                    }

                    sum = log10(sum);

                    mel.data[j*mel.n_len + i] = sum;
                }
            }
        }, iw);
    }

    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw].join();
    }

    // clamping and normalization
    double mmax = -1e20;
    for (int i = 0; i < mel.n_mel*mel.n_len; i++) {
        if (mel.data[i] > mmax) {
            mmax = mel.data[i];
        }
    }
    //printf("%s: max = %f\n", __func__, mmax);

    mmax -= 8.0;

    for (int i = 0; i < mel.n_mel*mel.n_len; i++) {
        if (mel.data[i] < mmax) {
            mel.data[i] = mmax;
        }

        mel.data[i] = (mel.data[i] + 4.0)/4.0;
    }

    return true;
}
