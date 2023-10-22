#include <iostream>
#include <fstream>
#include <vector>
#include <cmath>

int main() {
    //const char* filename = "MicInput_16000_mono_float.pcm";
	const char* filename = "english_test_3_bili_16000_mono_float.pcm";
	const char* outputPcmFile = "english_test_3_bili_16000_mono_float_silence_removed.pcm";

    // Open the WAV file
    std::ifstream file(filename, std::ios::binary);

    if (!file.is_open()) {
        std::cerr << "Error: Could not open the WAV file." << std::endl;
        return 1;
    }
	
	std::ofstream pcmFile(outputPcmFile, std::ios::binary);
    if (!pcmFile.is_open()) {
        std::cerr << "Error: Could not open the output PCM file." << std::endl;
        return 1;
    }

	const int BUFFER_SIZE = 512; // 32 milliseconds of audio data used for silence detection
	const double silenceThresholdDB = -35.0;  // Adjust the silence threshold as needed

	int seconds_counter = 0;
	int bytes_read_counter = 0;
		
	// Read and analyze the audio data
	std::vector<float> buffer(BUFFER_SIZE);
	while (file.read(reinterpret_cast<char*>(buffer.data()), sizeof(float) * BUFFER_SIZE)) {
	
		// For sampling rate 16000, reading 16000 samples equals to 1 second
		bytes_read_counter = bytes_read_counter + BUFFER_SIZE;
		if (bytes_read_counter > 16000) {
			bytes_read_counter = 0;
			seconds_counter++;
			std::cout << "seconds_counter:===========================> " << seconds_counter << std::endl;
		}
		
		double rms = 0.0;
		for (int i = 0; i < BUFFER_SIZE; i++) {
			float sample = buffer[i];
			rms += sample * sample;
		}

		rms = sqrt(rms / BUFFER_SIZE);
		double dB = 20 * log10(rms);

		if (dB < silenceThresholdDB) {
			std::cout << "Silence detected (dB: " << dB << ")." << std::endl;
		} else {
			std::cout << "(dB: " << dB << ")." << std::endl;
			pcmFile.write(reinterpret_cast<char*>(buffer.data()), BUFFER_SIZE);
		}
	}

    // Close files
	pcmFile.close();
    file.close();

    return 0;
}
