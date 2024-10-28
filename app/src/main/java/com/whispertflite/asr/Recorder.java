package com.whispertflite.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.whispertflite.utils.WaveUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {
    private static final String TAG = "Recorder";
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_RECORD = "Record";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private String mWavFilePath;
    private IRecorderListener mListener;
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

    public void setListener(IRecorderListener listener) {
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

        ByteBuffer buffer30Sec = ByteBuffer.allocateDirect(sampleRateInHz * bytesPerSample * channels * 30);
        ByteBuffer bufferRealtime = ByteBuffer.allocateDirect(sampleRateInHz * bytesPerSample * channels * 5);

        int totalBytesRead = 0;
        byte[] audioData = new byte[bufferSize];

        while (mInProgress.get() && totalBytesRead < buffer30Sec.capacity()) {
            int bytesRead = audioRecord.read(audioData, 0, bufferSize);
            if (bytesRead > 0) {
                buffer30Sec.put(audioData, 0, bytesRead);
                bufferRealtime.put(audioData, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (totalBytesRead / (sampleRateInHz * bytesPerSample * channels) % 3 == 0) {
                    bufferRealtime.flip();
                    float[] samples = convertToFloatArray(bufferRealtime);
                    bufferRealtime.clear();
                    sendData(samples);
                }
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                break;
            }
        }

        audioRecord.stop();
        audioRecord.release();

        WaveUtil.createWaveFile(mWavFilePath, buffer30Sec.array(), sampleRateInHz, channels, bytesPerSample);
        sendUpdate(MSG_RECORDING_DONE);

        // Notify the waiting thread that recording is complete
        synchronized (fileSavedLock) {
            fileSavedLock.notify(); // Notify that recording is finished
        }
    }

    private float[] convertToFloatArray(ByteBuffer buffer) {
        buffer.order(ByteOrder.nativeOrder());
        float[] samples = new float[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
    }
}
