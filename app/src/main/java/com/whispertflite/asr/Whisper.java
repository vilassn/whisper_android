package com.whispertflite.asr;

import android.content.Context;
import android.util.Log;

import com.whispertflite.engine.IWhisperEngine;
import com.whispertflite.engine.WhisperEngineJava;
import com.whispertflite.engine.WhisperEngineNative;
import com.whispertflite.engine.WhisperEngineTransl;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class Whisper {
    public static final String TAG = "Whisper";
    public static final String ACTION_TRANSLATE = "TRANSLATE";
    public static final String ACTION_TRANSCRIBE_JAVA = "TRANSCRIBE_JAVA";
    public static final String ACTION_TRANSCRIBE_NATIVE = "TRANSCRIBE_NATIVE";
    public static final int MSG_ID_EVENT = 'E';
    public static final int MSG_ID_RESULT = 'R';
    public static final String MSG_LOADING_MODEL = "Loading model and vocab...";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_FILE_NOT_FOUND = "Input file doesn't exist..!";

    private final Context mContext;
    private final String mModelPath;
    private final String mVocabPath;
    private final boolean mIsMultilingual;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private final IWhisperEngine mTranscribeEngineJava = new WhisperEngineJava();
    private final IWhisperEngine mTranscribeEngineNative = new WhisperEngineNative();
    private final IWhisperEngine mTranslateEngine = new WhisperEngineTransl();

    // TODO: use WhisperEngine as per requirement
    private final IWhisperEngine mWhisperEngine = mTranscribeEngineJava;

    private String mAction = null;
    private String mWavFilePath = null;
    private Thread mExecutorThread = null;
    private IOnUpdateListener mUpdateListener = null;

    public Whisper(Context context, String modelPath, String vocabPath, boolean isMultilingual) {
        mContext = context;
        mModelPath = modelPath;
        mVocabPath = vocabPath;
        mIsMultilingual = isMultilingual;
    }

    public void setUpdateListener(IOnUpdateListener listener) {
        mUpdateListener = listener;
        mTranscribeEngineJava.setUpdateListener(listener);
        mTranscribeEngineNative.setUpdateListener(listener);
        mTranslateEngine.setUpdateListener(listener);
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

//        if (mAction.equals(ACTION_TRANSLATE)) {
//            mWhisperEngine = mTranslateEngine;
//        } else if (mAction.equals(ACTION_TRANSCRIBE_NATIVE)) {
//            mWhisperEngine = mTranscribeEngineNative;
//        } else { // ACTION_TRANSCRIBE_JAVA <= default action
//            mWhisperEngine = mTranscribeEngineJava;
//        }

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

    private void updateStatus(int msgID, String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdate(msgID, message);
    }

    private void threadFunction() {
        try {
            // Initialize WhisperEngine
            if (!mWhisperEngine.isInitialized()) {
                updateStatus(MSG_ID_EVENT, MSG_LOADING_MODEL);
                mWhisperEngine.initialize(mIsMultilingual, mVocabPath, mModelPath);
            }

            // Get Transcription
            if (mWhisperEngine.isInitialized()) {
                Log.d(TAG, "WaveFile: " + mWavFilePath);

                File waveFile = new File(mWavFilePath);
                if (waveFile.exists()) {
                    long startTime = System.currentTimeMillis();
                    updateStatus(MSG_ID_EVENT, MSG_PROCESSING);

                    // Get transcription from wav file
                    String result = mWhisperEngine.getTranscription(mWavFilePath);

                    // Display output result
                    updateStatus(MSG_ID_RESULT, result);
                    Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);

                    // Calculate time required for transcription
                    long endTime = System.currentTimeMillis();
                    long timeTaken = endTime - startTime;
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                } else {
                    updateStatus(MSG_ID_EVENT, MSG_FILE_NOT_FOUND);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error...", e);
            updateStatus(MSG_ID_EVENT, e.getMessage());
        }
    }
}