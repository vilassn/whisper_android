#ifndef _TFLITEENGINE_H_
#define _TFLITEENGINE_H_

#include <string>
#include <vector>

class TFLiteEngine {
public:
    TFLiteEngine() {};
    ~TFLiteEngine() {};

    int loadModel(const char *modelPath, const bool isMultilingual);
    void freeModel();

    std::string transcribeBuffer(std::vector<float> samples);
    std::string transcribeFile(const char* waveFile);

private:
    // Add any private members or helper functions as needed
};

#endif // _TFLITEENGINE_H_

