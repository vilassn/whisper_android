#include <iostream>
#include "TFLiteEngine.h"

int main() {
    TFLiteEngine engine;
    bool isMultilingual = false;

    // Load the TFLite model and vocabulary
    const char* modelPath = "../../assets/whisper-tiny-en.tflite";
    if (isMultilingual) {
        modelPath = "../../assets/whisper-tiny.tflite";
    }

    int result = engine.loadModel(modelPath, isMultilingual);
    if (result != 0) {
        std::cerr << "Error loading the TFLite model or vocabulary." << std::endl;
        return 1;
    }

    // Transcribe an audio file
    const char* audioFilePath = "../../assets/jfk.wav";
    //audioFilePath = "../resources/MicInput.wav";
	audioFilePath = "../english_test_3_bili.wav";
    std::string transcription = engine.transcribeFile(audioFilePath);
    if (!transcription.empty()) {
        std::cout << "Transcription: " << transcription << std::endl;
    } else {
        std::cerr << "Error transcribing the audio file." << std::endl;
        return 2;
    }

    return 0;
}
