package com.example.tfliteaudio;

import java.io.FileOutputStream;
import java.io.IOException;

public class WavFileUtils {

    public static void writeWavHeader(FileOutputStream outputStream, int bufferSize, int sampleRate, int channels, int audioFormat) {
        // Calculate values required for the WAV header
        long totalDataLen = bufferSize + 36; // Total file size minus 8 bytes
        long totalAudioLen = bufferSize; // Length of audio data in bytes
        long byteRate = (long) sampleRate * channels * (audioFormat / 8);

        // WAV header
        byte[] header = new byte[44];

        // RIFF header
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xFF);
        header[5] = (byte) ((totalDataLen >> 8) & 0xFF);
        header[6] = (byte) ((totalDataLen >> 16) & 0xFF);
        header[7] = (byte) ((totalDataLen >> 24) & 0xFF);

        // WAVE header
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // Format chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // Size of format chunk (16 bytes)
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // PCM audio format (1 for PCM)
        header[21] = 0;
        header[22] = (byte) channels; // Number of channels
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xFF);
        header[25] = (byte) ((sampleRate >> 8) & 0xFF);
        header[26] = (byte) ((sampleRate >> 16) & 0xFF);
        header[27] = (byte) ((sampleRate >> 24) & 0xFF);
        header[28] = (byte) (byteRate & 0xFF);
        header[29] = (byte) ((byteRate >> 8) & 0xFF);
        header[30] = (byte) ((byteRate >> 16) & 0xFF);
        header[31] = (byte) ((byteRate >> 24) & 0xFF);
        header[32] = (byte) (channels * (audioFormat / 8)); // Block align
        header[33] = 0;
        header[34] = (byte) audioFormat; // Bits per sample
        header[35] = 0;

        // Data chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xFF);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xFF);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xFF);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xFF);

        // Write the header to the output stream
        try {
            outputStream.write(header);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
