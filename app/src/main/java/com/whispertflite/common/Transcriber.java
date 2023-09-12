package com.whispertflite.common;

import android.content.Context;
import android.util.Log;

import com.whispertflite.R;
import com.whispertflite.engine.TFLiteEngine;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class Transcriber {
    private final String TAG = "Transcriber";
    private Context mContext;
    private String mWavFilePath;
    private final String mModelPath;
    private final String mVocabPath;
    private final boolean mIsMultilingual;
    private Thread mTranscriptionThread = null;
    private IUpdateListener mUpdateListener = null;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    // TODO: use TFLiteEngine as per requirement
    private final ITFLiteEngine mTFLiteEngine = new TFLiteEngine();
    //    private final ITFLiteEngine mTFLiteEngine = new TFLiteEngineNative();
    //    private final ITFLiteEngine mTFLiteEngine = new TFLiteEngineTranslate();

    public Transcriber(Context context, String modelPath, String vocabPath, boolean isMultilingual) {
        mContext = context;
        mModelPath = modelPath;
        mVocabPath = vocabPath;
        mIsMultilingual = isMultilingual;
    }

    public void setUpdateListener(IUpdateListener listener) {
        mUpdateListener = listener;
    }

    public void setFilePath(String wavFile) {
        mWavFilePath = wavFile;
    }

    public void startTranscription() {
        mTranscriptionThread = new Thread(this::threadFunction);
        mTranscriptionThread.start();
    }

    public void stopTranscription() {
        mInProgress.set(false);
        try {
            if (mTranscriptionThread != null) {
                mTranscriptionThread.join();
                mTranscriptionThread = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isTranscriptionInProgress() {
        return mInProgress.get();
    }

    private void threadFunction() {
        try {
            mInProgress.set(true);

            // Initialize TFLiteEngine
            if (!mTFLiteEngine.isInitialized()) {
                mUpdateListener.onStatusChanged(mContext.getString(R.string.loading_model_and_vocab));
                mTFLiteEngine.initialize(mIsMultilingual, mVocabPath, mModelPath);
            }

            // Get Transcription
            if (mTFLiteEngine.isInitialized()) {
                Log.d(TAG, "WaveFile: " + mWavFilePath);

                File waveFile = new File(mWavFilePath);
                if (waveFile.exists()) {
                    long startTime = System.currentTimeMillis();
                    mUpdateListener.onStatusChanged(mContext.getString(R.string.transcribing));

                    // Get transcription from wav file
                    String result = mTFLiteEngine.getTranscription(mWavFilePath);

                    // Display output result
                    mUpdateListener.onStatusChanged(result);
                    Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);

                    // Calculate time required for transcription
                    long endTime = System.currentTimeMillis();
                    long timeTaken = endTime - startTime;
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                } else {
                    mUpdateListener.onStatusChanged(mContext.getString(R.string.input_file_doesn_t_exist));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error...", e);
            mUpdateListener.onStatusChanged(e.getMessage());
        } finally {
            mInProgress.set(false);
        }
    }
}