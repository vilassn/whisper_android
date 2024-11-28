package com.whispertflite.utils;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class WaveUtil {
    public static final String TAG = "WaveUtil";
    public static final String RECORDING_FILE = "MicInput.wav";

    public static void createWaveFile(String filePath, byte[] samples, int sampleRate, int numChannels, int bytesPerSample) {
        try {
            int dataSize = samples.length; // actual data size in bytes
            int audioFormat = (bytesPerSample == 2) ? 1 : (bytesPerSample == 4) ? 3 : 0; // PCM_16 = 1, PCM_FLOAT = 3

            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
            fileOutputStream.write("RIFF".getBytes(StandardCharsets.UTF_8)); // Write the "RIFF" chunk descriptor
            fileOutputStream.write(intToByteArray(36 + dataSize), 0, 4); // Total file size - 8 bytes
            fileOutputStream.write("WAVE".getBytes(StandardCharsets.UTF_8)); // Write the "WAVE" format
            fileOutputStream.write("fmt ".getBytes(StandardCharsets.UTF_8)); // Write the "fmt " sub-chunk
            fileOutputStream.write(intToByteArray(16), 0, 4); // Sub-chunk size (16 for PCM)
            fileOutputStream.write(shortToByteArray((short) audioFormat), 0, 2); // Audio format (1 for PCM)
            fileOutputStream.write(shortToByteArray((short) numChannels), 0, 2); // Number of channels
            fileOutputStream.write(intToByteArray(sampleRate), 0, 4); // Sample rate
            fileOutputStream.write(intToByteArray(sampleRate * numChannels * bytesPerSample), 0, 4); // Byte rate
            fileOutputStream.write(shortToByteArray((short) (numChannels * bytesPerSample)), 0, 2); // Block align
            fileOutputStream.write(shortToByteArray((short) (bytesPerSample * 8)), 0, 2); // Bits per sample
            fileOutputStream.write("data".getBytes(StandardCharsets.UTF_8)); // Write the "data" sub-chunk
            fileOutputStream.write(intToByteArray(dataSize), 0, 4); // Data size

            // Write audio samples
            fileOutputStream.write(samples);

            // Close the file output stream
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error...", e);
        }
    }

    public static float[] getSamples(String filePath) {
        try {
            FileInputStream fileInputStream = new FileInputStream(filePath);

            // Read the WAV file header
            byte[] header = new byte[44];
            fileInputStream.read(header);

            // Check if it's a valid WAV file (contains "RIFF" and "WAVE" markers)
            String headerStr = new String(header, 0, 4);
            if (!headerStr.equals("RIFF")) {
                System.err.println("Not a valid WAV file");
                return new float[0];
            }

            // Get the audio format details from the header
            int sampleRate = byteArrayToNumber(header, 24, 4);
            int bitsPerSample = byteArrayToNumber(header, 34, 2);
            if (bitsPerSample != 16 && bitsPerSample != 32) {
                System.err.println("Unsupported bits per sample: " + bitsPerSample);
                return new float[0];
            }

            // Get the size of the data section (all PCM data)
            int dataLength = fileInputStream.available(); // byteArrayToInt(header, 40, 4);

            // Calculate the number of samples
            int bytesPerSample = bitsPerSample / 8;
            int numSamples = dataLength / bytesPerSample;

            // Read the audio data
            byte[] audioData = new byte[dataLength];
            fileInputStream.read(audioData);
            ByteBuffer byteBuffer = ByteBuffer.wrap(audioData);
            byteBuffer.order(ByteOrder.nativeOrder());

            // Convert audio data to PCM_FLOAT format
            float[] samples = new float[numSamples];
            if (bitsPerSample == 16) {
                for (int i = 0; i < numSamples; i++) {
                    samples[i] = (float) (byteBuffer.getShort() / 32768.0);
                }
            } else if (bitsPerSample == 32) {
                for (int i = 0; i < numSamples; i++) {
                    samples[i] = byteBuffer.getFloat();
                }
            }

            return samples;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error...", e);
        }
        return new float[0];
    }

    // Convert a portion of a byte array into an integer or a short
    private static int byteArrayToNumber(byte[] bytes, int offset, int length) {
        int value = 0; // Start with an initial value of 0

        // Loop through the specified portion of the byte array
        for (int i = 0; i < length; i++) {
            // Extract a byte, ensure it's positive, and shift it to its position in the integer
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }

        return value; // Return the resulting integer value
    }

    private static byte[] intToByteArray(int value) {
        byte[] byteArray = new byte[4]; // Create a 4-byte array

        // Convert and store the bytes in little-endian order
        byteArray[0] = (byte) (value & 0xFF);         // Least significant byte (LSB)
        byteArray[1] = (byte) ((value >> 8) & 0xFF);  // Second least significant byte
        byteArray[2] = (byte) ((value >> 16) & 0xFF); // Second most significant byte
        byteArray[3] = (byte) ((value >> 24) & 0xFF); // Most significant byte (MSB)

        return byteArray;
    }

    private static byte[] shortToByteArray(int value) {
        byte[] byteArray = new byte[2]; // Create a 2-byte array

        // Convert and store the bytes in little-endian order
        byteArray[0] = (byte) (value & 0xFF);        // Least significant byte (LSB)
        byteArray[1] = (byte) ((value >> 8) & 0xFF); // Most significant byte (MSB)

        return byteArray;
    }
}
