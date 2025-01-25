package com.whispertflite.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.whispertflite.utils.WaveUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {

    public interface RecorderListener {
        void onUpdateReceived(String message);

        void onDataReceived(float[] samples);
    }

    private static final String TAG = "Recorder";
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_RECORD = "Record";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";
    public static final int RECORDING_DURATION = 60; //60 seconds

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private String mWavFilePath;
    private RecorderListener mListener;
    private final Lock lock = new ReentrantLock();
    private final Condition hasTask = lock.newCondition();
    private final Object fileSavedLock = new Object(); // Lock object for wait/notify

    private volatile boolean shouldStartRecording = false;

    private final Thread workerThread;

    public Recorder(Context context) {
        this.mContext = context;

        // Initialize and start the worker thread
        workerThread = new Thread(this::recordLoop);
        workerThread.start();
    }

    public void setListener(RecorderListener listener) {
        this.mListener = listener;
    }

    public void setFilePath(String wavFile) {
        this.mWavFilePath = wavFile;
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        lock.lock();
        try {
            shouldStartRecording = true;
            hasTask.signal();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        mInProgress.set(false);

        // Wait for the recording thread to finish
        synchronized (fileSavedLock) {
            try {
                fileSavedLock.wait(); // Wait until notified by the recording thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void sendUpdate(String message) {
        if (mListener != null)
            mListener.onUpdateReceived(message);
    }

    private void sendData(float[] samples) {
        if (mListener != null)
            mListener.onDataReceived(samples);
    }

    private void recordLoop() {
        while (true) {
            lock.lock();
            try {
                while (!shouldStartRecording) {
                    hasTask.await();
                }
                shouldStartRecording = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            // Start recording process
            try {
                recordAudio();
            } catch (Exception e) {
                Log.e(TAG, "Recording error...", e);
                sendUpdate(e.getMessage());
            } finally {
                mInProgress.set(false);
            }
        }
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted");
            sendUpdate("Permission not granted for recording");
            return;
        }

        sendUpdate(MSG_RECORDING);

        int channels = 1;
        int bytesPerSample = 2;
        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int audioSource = MediaRecorder.AudioSource.MIC;

        int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
        audioRecord.startRecording();

        // Calculate maximum byte counts for 30 seconds (for saving)
        int bytesForOneSecond = sampleRateInHz * bytesPerSample * channels;
        int bytesForThreeSeconds = bytesForOneSecond * 3;
        int bytesForThirtySeconds = bytesForOneSecond * 30;
        int bytesForSixtySeconds = bytesForOneSecond * RECORDING_DURATION;

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream(); // Buffer for saving data in wave file
        ByteArrayOutputStream realtimeBuffer = new ByteArrayOutputStream(); // Buffer for real-time processing

        byte[] audioData = new byte[bufferSize];
        int totalBytesRead = 0;

        while (mInProgress.get() && totalBytesRead < bytesForSixtySeconds) {
            int bytesRead = audioRecord.read(audioData, 0, bufferSize);
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead);  // Save all bytes read up to 30 seconds
                realtimeBuffer.write(audioData, 0, bytesRead); // Accumulate real-time audio data
                totalBytesRead += bytesRead;

                // Check if realtimeBuffer has more than 3 seconds of data
                if (realtimeBuffer.size() >= bytesForThreeSeconds) {
                    float[] samples = convertToFloatArray(ByteBuffer.wrap(realtimeBuffer.toByteArray()));
                    realtimeBuffer.reset(); // Clear the buffer for the next accumulation
                    sendData(samples); // Send real-time data for processing
                }
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                break;
            }
        }

        audioRecord.stop();
        audioRecord.release();

        // Save recorded audio data to file (up to 30 seconds)
        WaveUtil.createWaveFile(mWavFilePath, outputBuffer.toByteArray(), sampleRateInHz, channels, bytesPerSample);
        sendUpdate(MSG_RECORDING_DONE);

        // Notify the waiting thread that recording is complete
        synchronized (fileSavedLock) {
            fileSavedLock.notify(); // Notify that recording is finished
        }

//        moveFileToSdcard(mWavFilePath);
    }

    private float[] convertToFloatArray(ByteBuffer buffer) {
        buffer.order(ByteOrder.nativeOrder());
        float[] samples = new float[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
    }

    // Move file from /data/user/0/com.whispertflite/files/MicInput.wav to
    // sdcard path /storage/emulated/0/Android/data/com.whispertflite/files/MicInput.wav
    // Copy and delete the original file
    private void moveFileToSdcard(String waveFilePath) {
        File sourceFile = new File(waveFilePath);
        File destinationFile = new File(this.mContext.getExternalFilesDir(null), sourceFile.getName());
        try (FileInputStream inputStream = new FileInputStream(sourceFile);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            if (sourceFile.delete()) {
                Log.d("FileMove", "File moved successfully to " + destinationFile.getAbsolutePath());
            } else {
                Log.e("FileMove", "Failed to delete the original file.");
            }

        } catch (IOException e) {
            Log.e("FileMove", "File move failed", e);
        }
    }
}
