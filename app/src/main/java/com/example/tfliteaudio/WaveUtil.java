package com.example.tfliteaudio;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WaveUtil {

    private static final String TAG = "ASR";
    public static final int WAV_HEADER_SIZE = 44;
    public static final int BYTES_IN_FLOAT = 4;

    public static final int NO_OF_CHANNELS = 1;
    public static final int BYTES_PER_SAMPLE = 4;
    public static final int SAMPLE_RATE = 16000;
    public static final int BUFFER_SIZE_1SEC = SAMPLE_RATE * NO_OF_CHANNELS * BYTES_PER_SAMPLE;

    public static int getBufferSize1sec() {
        return BUFFER_SIZE_1SEC;
    }

    public static int getBufferSize30sec() {
        return BUFFER_SIZE_1SEC * 30;
    }

    public static float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        float[] samples = new float[bytes.length / BYTES_IN_FLOAT];
        for (int i = 0; buffer.remaining() >= BYTES_IN_FLOAT; i++)
            samples[i] = buffer.getFloat();
        return samples;
    }

    public static void createWaveFile(String wavePath, float[] data) throws IOException {

        int sampleRate = SAMPLE_RATE;
        short channels = NO_OF_CHANNELS;
        short bytesPerSample = BYTES_PER_SAMPLE;

        // calculate header values
        short blockAlign = (short) (bytesPerSample * channels);
        int byteRate = sampleRate * bytesPerSample * channels;
        int audioSize = data.length * BYTES_IN_FLOAT;
        int fileSize = audioSize + WAV_HEADER_SIZE;
        int duration = audioSize / byteRate;
        Log.d(TAG, "duration: " + duration + ", audioSize: " + audioSize);

        ByteBuffer buffer = ByteBuffer.allocate(audioSize);
        buffer.order(ByteOrder.nativeOrder());
        for (float datum : data)
            buffer.putFloat(datum);

        // The stream that writes the audio file to the disk
        DataOutputStream out = new DataOutputStream(new FileOutputStream(wavePath));

        // Write Header
        out.writeBytes("RIFF");// 0-4 ChunkId always RIFF
        out.writeInt(Integer.reverseBytes(fileSize));// 5-8 ChunkSize always audio-length +header-length(44)
        out.writeBytes("WAVE");// 9-12 Format always WAVE
        out.writeBytes("fmt ");// 13-16 Subchunk1 ID always "fmt " with trailing whitespace
        out.writeInt(Integer.reverseBytes(16)); // 17-20 Subchunk1 Size always 16
        out.writeShort(Short.reverseBytes((short) 3));// 21-22 Audio-Format, 1 for PCM PulseAudio, 3 for WAVE_FORMAT_IEEE_FLOAT
        out.writeShort(Short.reverseBytes(channels));// 23-24 Num-Channels 1 for mono, 2 for stereo
        out.writeInt(Integer.reverseBytes(sampleRate));// 25-28 Sample-Rate
        out.writeInt(Integer.reverseBytes(byteRate));// 29-32 Byte Rate
        out.writeShort(Short.reverseBytes(blockAlign));// 33-34 Block Align
        out.writeShort(Short.reverseBytes((short) (bytesPerSample * 8)));// 35-36 Bits-Per-Sample
        out.writeBytes("data");// 37-40 Subchunk2 ID always data
        out.writeInt(Integer.reverseBytes(audioSize));// 41-44 Subchunk 2 Size audio-length

        // Append the silent audio data or what you recorded from the mic
        out.write(buffer.array());
        out.close();// close the stream properly
    }

    public static float[] readWaveFile(String wavePath) throws IOException {
        byte[] pcmBytes = Files.readAllBytes(Paths.get(wavePath));
        float[] floatArray = byteArrayToFloatArray(pcmBytes);

        float[] samples = new float[floatArray.length - (WAV_HEADER_SIZE / Float.BYTES)];
        for (int i = (WAV_HEADER_SIZE / Float.BYTES); i < floatArray.length; i++)
            samples[i - (WAV_HEADER_SIZE / Float.BYTES)] = floatArray[i];

        return samples;
    }

    public static void pcmToWaveFile(String pcmFile, String wavePath) {
        try {
            // Read samples from pcm file
            byte[] pcmBytes = Files.readAllBytes(Paths.get(pcmFile));
            float[] samples = byteArrayToFloatArray(pcmBytes);

            // Write samples to wav file
            createWaveFile(wavePath, samples);
            Log.d(TAG, "WaveFile is created: " + wavePath);


            // Read samples from wav file
            samples = readWaveFile(wavePath);

            // Write samples to wav file
            createWaveFile(wavePath, samples);
            Log.d(TAG, "WaveFile is recreated: " + wavePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
