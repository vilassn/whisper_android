package com.example.tfliteaudio;

import android.media.AudioFormat;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class WaveUtil {

    private static final String TAG = "WaveUtil";


//    private static final short MONO = 1;
//    private static final short STEREO = 2;
    private static final int WAV_HEADER_SIZE = 44;
    public static final String RECORDING_FILE = "MicInput.wav";

//    public static final int SAMPLE_RATE = 16000;
//    public static final int BYTES_PER_SAMPLE = 2;
//    public static final int NO_OF_CHANNELS = 1;
//    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // should be as per NO_OF_CHANNELS
//    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT; // should be as per BYTES_PER_SAMPLE
//    public static final int BUFFER_SIZE_1_SEC = SAMPLE_RATE * NO_OF_CHANNELS * BYTES_PER_SAMPLE;
//    public static final int BUFFER_SIZE_30_SEC = BUFFER_SIZE_1_SEC * 30;

    // wav header Endianness and number of bytes in value
    private static final int[] type = {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1};
    private static final int[] nBytes = {4, 4, 4, 4, 4, 2, 2, 4, 4, 2, 2, 4, 4};

    private static ByteBuffer convertToNumber(byte[] bytes, int numOfBytes, int type) {
        ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
        if (type == 0) buffer.order(ByteOrder.BIG_ENDIAN);
        else buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(bytes);
        buffer.rewind();
        return buffer;
    }

    private static float convertToFloat(byte[] bytes) {
        float num = 0.0f;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        if (bytes.length == 2)
            num = (float) buffer.getShort() / 32768.0f;
        else if (bytes.length == 4)
            num = buffer.getFloat();

        return num;
    }

    private static short reverseShortBytes(short number) {
        short num = 0;
        num = (short) (num | ((number & 0xFF) << 8)); // Reverse the first byte
        num = (short) (num | ((number & 0xFF00) >>> 8)); // Reverse the second byte
        return num;
    }

    private static int reverseIntBytes(int number) {
        int num = 0;
        num = num | ((number & 0xFF) << 24);   // Reverse the first byte
        num = num | ((number & 0xFF00) << 8);  // Reverse the second byte
        num = num | ((number & 0xFF0000) >>> 8);  // Reverse the third byte
        num = num | ((number & 0xFF000000) >>> 24);  // Reverse the fourth byte
        return num;
    }

    public static float[] getSamples(byte[] bytes, int sampleSize) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        float[] samples = new float[bytes.length / sampleSize];
        int i = 0;
        while (buffer.remaining() >= sampleSize) {
            samples[i] = buffer.getFloat();
            i++;
        }

        return samples;
    }

    public static void createWaveFile(String wavePath, byte[] samples, int sampleRate1, int channels1, int bytesPerSample1) throws IOException {
        // get audio params
        int sampleRate = sampleRate1;
        short channels = (short) channels1;
        short bytesPerSample = (short) bytesPerSample1;

        // calculate header values
        int audioSize = (samples.length / 4) * bytesPerSample;
        short blockAlign = (short) (bytesPerSample * channels);
        int byteRate = sampleRate * bytesPerSample * channels;
        int fileSize = audioSize + WAV_HEADER_SIZE;
        float duration = (float) audioSize / byteRate;
        Log.d(TAG, "duration: " + duration + ", audioSize: " + audioSize);

        // The stream that writes the audio file to the disk
        DataOutputStream out = new DataOutputStream(new FileOutputStream(wavePath));

        // Write Header
        out.writeBytes("RIFF");
        out.writeInt(reverseIntBytes(fileSize - 8)); // Placeholder for file size
        out.writeBytes("WAVE");
        out.writeBytes("fmt ");
        out.writeInt(reverseIntBytes(16)); // Subchunk1Size
        out.writeShort(reverseShortBytes((short) 1)); // AudioFormat PCM_16 = 1, PCM_FLOAT = 3
        out.writeShort(reverseShortBytes(channels)); // NumChannels
        out.writeInt(reverseIntBytes(sampleRate)); // SampleRate
        out.writeInt(reverseIntBytes(byteRate));  // ByteRate
        out.writeShort(reverseShortBytes(blockAlign)); // BlockAlign
        out.writeShort(reverseShortBytes((short) (bytesPerSample * 8))); // BitsPerSample
        out.writeBytes("data"); // Subchunk2 ID always data
        out.writeInt(reverseIntBytes(audioSize)); // Placeholder for data size

        // Append audio data
//        ByteBuffer buffer = ByteBuffer.allocate(audioSize);
//        buffer.order(ByteOrder.nativeOrder());
//        for (float sample : samples) {
//            buffer.putFloat(sample);
//        }

        out.write(samples);

        // close the stream properly
        out.close();
    }

    public static float[] readWaveFile(String wavePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(wavePath));
        ByteBuffer buffer = ByteBuffer.wrap(bytes, WAV_HEADER_SIZE, bytes.length - WAV_HEADER_SIZE);
        buffer.order(ByteOrder.nativeOrder());

        ArrayList<Float> dataVector = new ArrayList<>();
        while (buffer.remaining() >= 2) {
            //dataVector.add(buffer.getFloat());
            dataVector.add((float) buffer.getShort() / 32768.0f);
        }

        float[] data = new float[dataVector.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = dataVector.get(i);
        }

        return data;
    }

    public static float[] readAudioFile(String audioFile) {
        try {
            File file = new File(audioFile);
            int length = (int) file.length();
            System.out.println("length: " + length);

            short numChannels = 0;
            short bitsPerSample = 0;
            InputStream inputStream = new FileInputStream(file);
            for (int index = 0; index < nBytes.length; index++) {
                byte[] byteArray = new byte[nBytes[index]];
                int r = inputStream.read(byteArray, 0, nBytes[index]);
                ByteBuffer byteBuffer = convertToNumber(byteArray, nBytes[index], type[index]);
                if (index == 0) {
                    String chunkID = new String(byteArray);
                    System.out.println("chunkID: " + chunkID);
                } else if (index == 1) {
                    int chunkSize = byteBuffer.getInt();
                    System.out.println("chunkSize: " + chunkSize);
                } else if (index == 2) {
                    String format = new String(byteArray);
                    System.out.println("format: " + format);
                } else if (index == 3) {
                    String subChunk1ID = new String(byteArray);
                    System.out.println("subChunk1ID: " + subChunk1ID);
                } else if (index == 4) {
                    int subChunk1Size = byteBuffer.getInt();
                    System.out.println("subChunk1Size: " + subChunk1Size);
                } else if (index == 5) {
                    short audioFomart = byteBuffer.getShort();
                    System.out.println("audioFomart: " + audioFomart);
                } else if (index == 6) {
                    numChannels = byteBuffer.getShort();
                    System.out.println("numChannels: " + numChannels);
                } else if (index == 7) {
                    int sampleRate = byteBuffer.getInt();
                    System.out.println("sampleRate: " + sampleRate);
                } else if (index == 8) {
                    int byteRate = byteBuffer.getInt();
                    System.out.println("byteRate: " + byteRate);
                } else if (index == 9) {
                    short blockAlign = byteBuffer.getShort();
                    System.out.println("blockAlign: " + blockAlign);
                } else if (index == 10) {
                    bitsPerSample = byteBuffer.getShort();
                    System.out.println("bitsPerSample: " + bitsPerSample);
                } else if (index == 11) {
                    byte[] byteArray2 = new byte[4];
                    r = inputStream.read(byteArray2, 0, 4);
                    byteBuffer = convertToNumber(byteArray2, 4, 1);
                    int temp = byteBuffer.getInt();
                    //redundant data reading
                    byte[] byteArray3 = new byte[temp];
                    r = inputStream.read(byteArray3, 0, temp);
                    r = inputStream.read(byteArray2, 0, 4);
                    String subChunk2ID = new String(byteArray2);
                    if (subChunk2ID.compareTo("data") == 0) {
                        continue;
                    } else if (subChunk2ID.compareTo("LIST") == 0) {
                        byteBuffer = convertToNumber(byteArray2, 4, 1);
                        temp = byteBuffer.getInt();
                        //redundant data reading
                        byteArray3 = new byte[temp];
                        r = inputStream.read(byteArray3, 0, temp);
                        r = inputStream.read(byteArray2, 0, 4);
                        subChunk2ID = new String(byteArray2);
                    }
                } else if (index == 12) {
                    int subChunk2Size = byteBuffer.getInt();
                    System.out.println("subChunk2Size: " + subChunk2Size);
                }
            }

            ArrayList<Float> samples = new ArrayList<>();
            int bytePerSample = bitsPerSample / 8;
            byte[] buffer = new byte[bytePerSample];

            while (true) {
                int ret = 0;
                float sample = 0.0f;
                if (numChannels == 1) {
                    ret = inputStream.read(buffer, 0, bytePerSample);
                    sample = convertToFloat(buffer);
                } else if (numChannels == 2) {
                    ret = inputStream.read(buffer, 0, bytePerSample);
                    float sampleLeft = convertToFloat(buffer);
                    ret = inputStream.read(buffer, 0, bytePerSample);
                    float sampleRight = convertToFloat(buffer);
                    sample = (sampleLeft + sampleRight) / 2.0f;
                }

                samples.add(sample);
                if (ret == -1)
                    break;
            }

            float[] result = new float[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                result[i] = samples.get(i);
            }

            return result;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return new float[1];
        }
    }

    public static void getSamples(String filePath) {
        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            byte[] header = new byte[44]; // WAV file header is 44 bytes
            fileInputStream.read(header);

            // Parse the WAV header to extract audio format information
            int sampleRate = byteArrayToInt(header, 24, 28);
            int bitsPerSample = byteArrayToInt(header, 34, 36);
            int numberOfChannels = byteArrayToInt(header, 22, 24);

            // Determine the size of each sample in bytes
            int bytesPerSample = bitsPerSample / 8;

            // Read the audio samples from the WAV file
            byte[] audioData = new byte[fileInputStream.available()];
            fileInputStream.read(audioData);

            // At this point, audioData contains the raw audio samples.

            // You can process the audio data as needed.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int byteArrayToInt(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
