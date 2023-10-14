/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
#include "tensorflow/lite/core/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/optional_debug_tools.h"
#include "whisper.h"
#include "input_features.h"

// This is an example that is minimal to read a model
// from disk and perform inference. There is no data being loaded
// that is up to you to add as a user.
//
// NOTE: Do not add any dependencies to this that cannot be built with
// the minimal makefile. This example must remain trivial to build with
// the minimal build tool.
//
// Usage: minimal <tflite model>


#define TFLITE_MINIMAL_CHECK(x)                              \
  if (!(x)) {                                                \
    fprintf(stderr, "Error at %s:%d\n", __FILE__, __LINE__); \
    exit(1);                                                 \
  }

int main(int argc, char* argv[]) {
  if ((argc != 2) && (argc != 3)) {
    fprintf(stderr, "'minimal <tflite model>' or 'minimal <tflite model> <pcm_file name>'\n");
    return 1;
  }
  const char* filename = argv[1];
  whisper_filters filters;
  whisper_mel mel;
  struct timeval start_time,end_time;
  std::string word;
  int32_t n_vocab = 0;
  std::string fname = "./filters_vocab_en.bin";
  auto fin = std::ifstream(fname, std::ios::binary);
  {
    uint32_t magic=0;
    fin.read((char *) &magic, sizeof(magic));
    //@magic:USEN
    if (magic != 0x5553454e) {
        printf("%s: invalid vocab file '%s' (bad magic)\n", __func__, fname.c_str());
        return 0;
    }
  }

  // load mel filters
  {
      fin.read((char *) &filters.n_mel, sizeof(filters.n_mel));
      fin.read((char *) &filters.n_fft, sizeof(filters.n_fft));

      filters.data.resize(filters.n_mel * filters.n_fft);
      fin.read((char *) filters.data.data(), filters.data.size() * sizeof(float));
  }

  // load vocab
  {
    fin.read((char *) &n_vocab, sizeof(n_vocab));
    g_vocab.n_vocab = n_vocab;
    printf("\nn_vocab:%d\n",(int)n_vocab);

    for (int i = 0; i < n_vocab; i++) {
      uint32_t len;
      fin.read((char *) &len, sizeof(len));

      word.resize(len);
      fin.read((char *) word.data(), len);
      g_vocab.id_to_token[i] = word;
      //printf("len:%d",(int)len);
      //printf("'%s'\n", g_vocab.id_to_token[i].c_str());
    }

    g_vocab.n_vocab = 51864;//add additional vocab ids
    if (g_vocab.is_multilingual()) {
        g_vocab.token_eot++;
        g_vocab.token_sot++;
        g_vocab.token_prev++;
        g_vocab.token_solm++;
        g_vocab.token_not++;
        g_vocab.token_beg++;
    }
    for (int i = n_vocab; i < g_vocab.n_vocab; i++) {
        if (i > g_vocab.token_beg) {
            word = "[_TT_" + std::to_string(i - g_vocab.token_beg) + "]";
        } else if (i == g_vocab.token_eot) {
            word = "[_EOT_]";
        } else if (i == g_vocab.token_sot) {
            word = "[_SOT_]";
        } else if (i == g_vocab.token_prev) {
            word = "[_PREV_]";
        } else if (i == g_vocab.token_not) {
            word = "[_NOT_]";
        } else if (i == g_vocab.token_beg) {
            word = "[_BEG_]";
        } else {
            word = "[_extra_token_" + std::to_string(i) + "]";
        }
        g_vocab.id_to_token[i] = word;
        // printf("%s: g_vocab[%d] = '%s'\n", __func__, i, word.c_str());
    }
  }

  //Generate input_features for Audio file
  if (argc == 3) {
    const char* pcmfilename = argv[2];
    // WAV input
    std::vector<float> pcmf32;
    {
      drwav wav;
      if (!drwav_init_file(&wav, pcmfilename, NULL)) {
          fprintf(stderr, "%s: failed to open WAV file '%s' - check your input\n", argv[0],pcmfilename);
        //  whisper_print_usage(argc, argv, {});
          return 3;
      }

      if (wav.channels != 1 && wav.channels != 2) {
          fprintf(stderr, "%s: WAV file '%s' must be mono or stereo\n", argv[0], pcmfilename);
          return 4;
      }

      if (wav.sampleRate != WHISPER_SAMPLE_RATE) {
          fprintf(stderr, "%s: WAV file '%s' must be 16 kHz\n", argv[0], pcmfilename);
          return 5;
      }

      if (wav.bitsPerSample != 16) {
          fprintf(stderr, "%s: WAV file '%s' must be 16-bit\n", argv[0], pcmfilename);
          return 6;
      }

      int n = wav.totalPCMFrameCount;

      std::vector<int16_t> pcm16;
      pcm16.resize(n*wav.channels);
      drwav_read_pcm_frames_s16(&wav, n, pcm16.data());
      drwav_uninit(&wav);
      // convert to mono, float
      pcmf32.resize(n);
      if (wav.channels == 1) {
          for (int i = 0; i < n; i++) {
              pcmf32[i] = float(pcm16[i])/32768.0f;
          }
      } else {
          for (int i = 0; i < n; i++) {
              pcmf32[i] = float(pcm16[2*i] + pcm16[2*i + 1])/65536.0f;
          }
      }
    }

    //Hack if the audio file size is less than 30ms append with 0's
    pcmf32.resize((WHISPER_SAMPLE_RATE*WHISPER_CHUNK_SIZE),0);
    if (!log_mel_spectrogram(pcmf32.data(), pcmf32.size(), WHISPER_SAMPLE_RATE, WHISPER_N_FFT, WHISPER_HOP_LENGTH, WHISPER_N_MEL, 1,filters, mel)) {
      fprintf(stderr, "%s: failed to compute mel spectrogram\n", __func__);
      return -1;
    }

    printf("\nmel.n_len%d\n",mel.n_len);
    printf("\nmel.n_mel:%d\n",mel.n_mel);
    print(mel.data);
  }//end of audio file processing

  // Load tflite model
  std::unique_ptr<tflite::FlatBufferModel> model =
      tflite::FlatBufferModel::BuildFromFile(filename);
  TFLITE_MINIMAL_CHECK(model != nullptr);

  // Build the interpreter with the InterpreterBuilder.
  // Note: all Interpreters should be built with the InterpreterBuilder,
  // which allocates memory for the Interpreter and does various set up
  // tasks so that the Interpreter can read the provided model.
  tflite::ops::builtin::BuiltinOpResolver resolver;
  tflite::InterpreterBuilder builder(*model, resolver);
  std::unique_ptr<tflite::Interpreter> interpreter;
  builder(&interpreter);
  TFLITE_MINIMAL_CHECK(interpreter != nullptr);

  // Allocate tensor buffers.
  TFLITE_MINIMAL_CHECK(interpreter->AllocateTensors() == kTfLiteOk);
  //printf("=== Pre-invoke Interpreter State ===\n");
  // tflite::PrintInterpreterState(interpreter.get());
  // Get information about the memory area to use for the model's input.
  float* input = interpreter->typed_input_tensor<float>(0);
  if (argc == 2) {
    memcpy(input, _content_input_features_bin, WHISPER_N_MEL*WHISPER_MEL_LEN*sizeof(float)); //to load pre generated input_features
  }
  else if (argc == 3) {
    memcpy(input, mel.data.data(), mel.n_mel*mel.n_len*sizeof(float));
  }
  // Fill input buffers
  // TODO(user): Insert code to fill input tensors.
  // Note: The buffer of the input tensor with index `i` of type T can
  // be accessed with `T* input = interpreter->typed_input_tensor<T>(i);`
  gettimeofday(&start_time, NULL);
  // Run inference
  TFLITE_MINIMAL_CHECK(interpreter->Invoke() == kTfLiteOk);
  gettimeofday(&end_time, NULL);
  printf("Inference time %ld seconds \n",(end_time.tv_sec-start_time.tv_sec));
  int output = interpreter->outputs()[0];
  TfLiteTensor *output_tensor = interpreter->tensor(output);
  TfLiteIntArray *output_dims = output_tensor->dims;
  // assume output dims to be something like (1, 1, ... ,size)
  auto output_size = output_dims->data[output_dims->size - 1];
  //printf("output size:%d\n",output_size );
  int *output_int = interpreter->typed_output_tensor<int>(0);
  std::string text = "";
  std::string word_add;
  for (int i = 0; i < output_size; i++) {
    //printf("%d\t",output_int[i]);
    if(output_int[i] == g_vocab.token_eot){
      break;
    }
    text += whisper_token_to_str(output_int[i]);
  }
  printf("\n%s\n", text.c_str());
  printf("\n");

  //printf("\n\n=== Post-invoke Interpreter State ===\n");
  ////  tflite::PrintInterpreterState(interpreter.get());
  // Read output buffers
  // TODO(user): Insert getting data out code.
  // Note: The buffer of the output tensor with index `i` of type T can
  // be accessed with `T* output = interpreter->typed_output_tensor<T>(i);`
  return 0;
}
