package com.whispertflite.asr;

import android.content.Context;
import android.util.Log;

import com.whispertflite.engine.IWhisperEngine;
import com.whispertflite.engine.WhisperEngineNative;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Whisper {
    public static final String TAG = "Whisper";
    public static final String ACTION_TRANSLATE = "TRANSLATE";
    public static final String ACTION_TRANSCRIBE = "TRANSCRIBE";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done...!";
    public static final String MSG_FILE_NOT_FOUND = "Input file doesn't exist..!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private final Object mAudioBufferQueueLock = new Object();  // Synchronization object
    private final Object mWhisperEngineLock = new Object();  // Synchronization object
    private final Queue<float[]> audioBufferQueue = new LinkedList<>();
    private Thread mMicTranscribeThread = null;

    // TODO: use WhisperEngine as per requirement
//    private final IWhisperEngine mWhisperEngine = new WhisperEngine();
    private final IWhisperEngine mWhisperEngine = new WhisperEngineNative();
//    private final IWhisperEngine mWhisperEngine = new WhisperEngineTwoModel();

    private String mAction = null;
    private String mWavFilePath = null;
    private Thread mExecutorThread = null;
    private IWhisperListener mUpdateListener = null;

    public Whisper(Context context) {
        mContext = context;
    }

    public void setListener(IWhisperListener listener) {
        mUpdateListener = listener;
        mWhisperEngine.setUpdateListener(mUpdateListener);
    }

    public void loadModel(String modelPath, String vocabPath, boolean isMultilingual) {
        try {
            mWhisperEngine.initialize(modelPath, vocabPath, isMultilingual);

            // Start thread for mic data transcription in realtime
            startMicTranscriptionThread();
        } catch (IOException e) {
            Log.e(TAG, "Error...", e);
        }
    }

    public void setAction(String action) {
        mAction = action;
    }

    public void setFilePath(String wavFile) {
        mWavFilePath = wavFile;
    }

    public void start() {
        if (mInProgress.get()) {
            Log.d(TAG, "Execution is already in progress...");
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
                mWhisperEngine.interrupt();
                //mExecutorThread.interrupt();
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

    private void sendUpdate(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdateReceived(message);
    }

    private void sendResult(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onResultReceived(message);
    }

    private void threadFunction() {
        try {
            // Get Transcription
            if (mWhisperEngine.isInitialized()) {
                Log.d(TAG, "WaveFile: " + mWavFilePath);

                File waveFile = new File(mWavFilePath);
                if (waveFile.exists()) {
                    long startTime = System.currentTimeMillis();
                    sendUpdate(MSG_PROCESSING);

//                    String result = "";
//                    if (mAction.equals(ACTION_TRANSCRIBE))
//                        result = mWhisperEngine.getTranscription(mWavFilePath);
//                    else if (mAction == ACTION_TRANSLATE)
//                        result = mWhisperEngine.getTranslation(mWavFilePath);

                    // Get result from wav file
                    synchronized (mWhisperEngineLock) {
                        String result = mWhisperEngine.transcribeFile(mWavFilePath);
                        sendResult(result);
                        Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);
                    }

                    sendUpdate(MSG_PROCESSING_DONE);

                    // Calculate time required for transcription
                    long endTime = System.currentTimeMillis();
                    long timeTaken = endTime - startTime;
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                } else {
                    sendUpdate(MSG_FILE_NOT_FOUND);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error...", e);
            sendUpdate(e.getMessage());
        }
    }

    // Write buffer in Queue
    public void writeBuffer(float[] samples) {
        synchronized (mAudioBufferQueueLock) {
            audioBufferQueue.add(samples);
            mAudioBufferQueueLock.notify(); // Notify waiting threads
        }
    }

    // Read buffer from Queue
    private float[] readBuffer() {
        synchronized (mAudioBufferQueueLock) {
            while (audioBufferQueue.isEmpty()) {
                try {
                    // Wait for the queue to have data
                    mAudioBufferQueueLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return audioBufferQueue.poll();
        }
    }

    // Mic data transcription thread in realtime
    private void startMicTranscriptionThread() {
        if(mMicTranscribeThread == null) {
            // Create a transcribe thread
            mMicTranscribeThread = new Thread(() -> {
                while (true) {
                    float[] samples = readBuffer();
                    if (samples != null) {
                        synchronized (mWhisperEngineLock) {
                            String result = mWhisperEngine.transcribeBuffer(samples);
                            sendResult(result);
                        }
                    }
                }
            });

            // Start the transcribe thread
            mMicTranscribeThread.start();
        }
    }
}