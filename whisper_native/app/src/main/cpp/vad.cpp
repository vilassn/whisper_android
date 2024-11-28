#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define SAMPLE_RATE (44100)
#define FRAME_SIZE (512)
#define BUFFER_SIZE (2048)
#define VAD_THRESHOLD (0.01f)

typedef struct {
    float buffer[BUFFER_SIZE];
    float energy;
} Frame;

int main(void) {
    FILE *fp = fopen("audio.raw", "rb");  // Replace with your input file
    if (fp == NULL) {
        fprintf(stderr, "Error opening input file.\n");
        return 1;
    }

    float buffer[BUFFER_SIZE];
    Frame frame = {0};
    int isSpeech = 0;
    int numFrames = 0;

    while (fread(buffer, sizeof(float), BUFFER_SIZE, fp) == BUFFER_SIZE) {
        for (int i = 0; i < BUFFER_SIZE; i += FRAME_SIZE) {
            frame.energy = 0.0f;

            for (int j = i; j < i + FRAME_SIZE; j++) {
                frame.buffer[j - i] = buffer[j];
                frame.energy += buffer[j] * buffer[j];
            }

            frame.energy = sqrt(frame.energy / FRAME_SIZE);

            if (frame.energy > VAD_THRESHOLD) {
                isSpeech = 1;
            } else {
                isSpeech = 0;
            }

            printf("Frame %d: %s\n", numFrames, isSpeech ? "Speech" : "Silence");
            numFrames++;
        }
    }

    fclose(fp);
    return 0;
}
