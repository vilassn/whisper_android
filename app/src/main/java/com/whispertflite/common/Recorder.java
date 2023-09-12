package com.whispertflite.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.whispertflite.R;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class Recorder {
    private final String TAG = "Recorder";
    private Context mContext;
    private String mWavFile;
    private Thread mThread = null;
    private IUpdateListener mUpdateListener = null;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    public Recorder(Context context) {
        mContext = context;
    }

    public void setUpdateListener(IUpdateListener listener) {
        mUpdateListener = listener;
    }

    public void setFilePath(String wavFile) {
        mWavFile = wavFile;
    }

    public void startRecording() {
        mThread = new Thread(this::threadFunction);
        mThread.start();
    }

    public void stopRecording() {
        mInProgress.set(false);
        try {
            if (mThread != null) {
                mThread.join();
                mThread = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isRecordingInProgress() {
        return mInProgress.get();
    }

    private void threadFunction() {
        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            mInProgress.set(true);
            mUpdateListener.onStatusChanged(mContext.getString(R.string.recording));

            int channels = 1;
            int bytesPerSample = 2;
            int sampleRateInHz = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO; // as per channels
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // as per bytesPerSample
            int audioSource = MediaRecorder.AudioSource.MIC;

            int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
            audioRecord.startRecording();

            int durationInSeconds = 30;
            int bufferSize30Sec = durationInSeconds * sampleRateInHz * bytesPerSample * channels;
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize30Sec);

            int totalBytesRead = 0;
            byte[] buffer = new byte[bufferSize];
            while (mInProgress.get() && (totalBytesRead < bufferSize30Sec)) {
                int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                if (bytesRead > 0) {
                    byteBuffer.put(buffer, 0, bytesRead);
                } else {
                    Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                }

                totalBytesRead = totalBytesRead + bytesRead;
            }

            audioRecord.stop();
            audioRecord.release();

            WaveUtil.createWaveFile(mWavFile, byteBuffer.array(), sampleRateInHz, channels, bytesPerSample);
            Log.d(TAG, "Recorded file: " + mWavFile);
            mUpdateListener.onStatusChanged(mContext.getString(R.string.recording_is_completed));
        } catch (Exception e) {
            throw new RuntimeException("Writing of recorded audio failed", e);
        } finally {
            mInProgress.set(false);
        }
    }
}
