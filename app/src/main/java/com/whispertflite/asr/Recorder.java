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
import java.util.concurrent.atomic.AtomicBoolean;

public class Recorder {
    public static final String TAG = "Recorder";
    public static final int MSG_ID_EVENT = 'E';
    public static final int MSG_ID_RESULT = 'R';
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_RECORD = "Record";
    public static final String MSG_RECORDING_PROGRESS = "Recording...";
    public static final String MSG_RECORDING_STARTED = "Recording is started..!";
    public static final String MSG_RECORDING_COMPLETED = "Recording is completed..!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private String mWavFilePath = null;
    private Thread mExecutorThread = null;
    private IOnUpdateListener mUpdateListener = null;

    public Recorder(Context context) {
        mContext = context;
    }

    public void setUpdateListener(IOnUpdateListener listener) {
        mUpdateListener = listener;
    }

    public void setFilePath(String wavFile) {
        mWavFilePath = wavFile;
    }

    public void start() {
        if (mInProgress.get()) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }

        mExecutorThread = new Thread(() -> {
            mInProgress.set(true);
            threadFunction();
            mInProgress.set(false);
        });
        mExecutorThread.start();
    }

    public void stop() {
        mInProgress.set(false);
        try {
            if (mExecutorThread != null) {
                mExecutorThread.join();
                mExecutorThread = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void updateStatus(int msgID, String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdate(msgID, message);
    }

    private void threadFunction() {
        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "AudioRecord permission is not granted");
                return;
            }

            updateStatus(MSG_ID_EVENT, MSG_RECORDING_STARTED);

            int channels = 1;
            int bytesPerSample = 2;
            int sampleRateInHz = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO; // as per channels
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // as per bytesPerSample
            int audioSource = MediaRecorder.AudioSource.MIC;

            int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
            audioRecord.startRecording();

            int bufferSize1Sec = sampleRateInHz * bytesPerSample * channels;
            int bufferSize30Sec = bufferSize1Sec * 30;
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize30Sec);

            int timer = 0;
            int totalBytesRead = 0;
            byte[] buffer = new byte[bufferSize];
            while (mInProgress.get() && (totalBytesRead < bufferSize30Sec)) {
                int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                if (bytesRead > 0) {
                    byteBuffer.put(buffer, 0, bytesRead);
                } else {
                    Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                    break;
                }

                totalBytesRead = totalBytesRead + bytesRead;
                int timer_tmp = totalBytesRead / bufferSize1Sec;
                if (timer != timer_tmp) {
                    timer = timer_tmp;
//                    Log.d(TAG, "updating timer: " + timer);
                    updateStatus(MSG_ID_EVENT, MSG_RECORDING_PROGRESS + timer + "s");
                }
            }

            audioRecord.stop();
            audioRecord.release();

            WaveUtil.createWaveFile(mWavFilePath, byteBuffer.array(), sampleRateInHz, channels, bytesPerSample);
            Log.d(TAG, "Recorded file: " + mWavFilePath);

            updateStatus(MSG_ID_EVENT, MSG_RECORDING_COMPLETED);
        } catch (Exception e) {
            Log.e(TAG, "Error...", e);
            updateStatus(MSG_ID_EVENT, e.getMessage());
        }
    }
}
