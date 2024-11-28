#include <iostream>
#include <fstream>
#include <vector>

int main() {
    const char* inputWavFile = "MicInput.wav";
    const char* outputPcmFile = "output.pcm";

    std::ifstream wavFile(inputWavFile, std::ios::binary);

    if (!wavFile.is_open()) {
        std::cerr << "Error: Could not open the input WAV file." << std::endl;
        return 1;
    }

    // Skip the WAV header (44 bytes for standard 16-bit, mono WAV)
    const int headerSize = 44;
    wavFile.seekg(headerSize);

    std::ofstream pcmFile(outputPcmFile, std::ios::binary);
    if (!pcmFile.is_open()) {
        std::cerr << "Error: Could not open the output PCM file." << std::endl;
        wavFile.close();
        return 1;
    }

    const int BUFFER_SIZE = 1024;
    std::vector<char> buffer(BUFFER_SIZE);
    
    while (wavFile.read(buffer.data(), BUFFER_SIZE)) {
        pcmFile.write(buffer.data(), wavFile.gcount());
    }

    pcmFile.close();
    wavFile.close();

    std::cout << "WAV header removed, PCM data saved." << std::endl;
    return 0;
}
