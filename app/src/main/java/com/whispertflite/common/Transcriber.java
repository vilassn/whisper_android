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
    private String mWavFile;
    private Thread mThread = null;
    private IUpdateListener mUpdateListener = null;

    private final ITFLiteEngine mTFLiteEngine = new TFLiteEngine();
//    private final IEngine mTFLiteEngine = new TFLiteEngineNative();
//    private final ITFLiteEngine mTFLiteEngine = new TFLiteEngineTranslate();
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    public Transcriber(Context context) {
        mContext = context;
    }

    public void setUpdateListener(IUpdateListener listener) {
        mUpdateListener = listener;

    }

    public void setFilePath(String wavFile) {
        mWavFile = wavFile;
    }

    public void startTranscription() {
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

    public boolean isTranscriptionInProgress() {
        return mInProgress.get();
    }

    private void threadFunction() {
        try {
            mInProgress.set(true);

            // Initialize TFLiteEngine
            if (!mTFLiteEngine.isInitialized()) {
                // Update progress to UI thread
                mUpdateListener.onStatusChanged(mContext.getString(R.string.loading_model_and_vocab));

                // set true for multilingual support
                // whisper.tflite => not multilingual
                // whisper-small.tflite => multilingual
                // whisper-tiny.tflite => multilingual
                boolean isMultilingual = true;

                // Get Model and vocab file paths
                String modelPath;
                String vocabPath;
                String filesDir = new File(mWavFile).getParent();
                if (isMultilingual) {
                    modelPath = filesDir + File.separator + "whisper-tiny.tflite";
                    vocabPath = filesDir + File.separator + "filters_vocab_multilingual.bin";
                } else {
                    modelPath = filesDir + File.separator + "whisper-tiny-en.tflite";
                    vocabPath = filesDir + File.separator + "filters_vocab_gen.bin";
                }

                mTFLiteEngine.initialize(isMultilingual, vocabPath, modelPath);
            }

            // Get Transcription
            if (mTFLiteEngine.isInitialized()) {
                Log.d(TAG, "WaveFile: " + mWavFile);

                if (new File(mWavFile).exists()) {
                    // Update progress to UI thread
                    mUpdateListener.onStatusChanged(mContext.getString(R.string.transcribing));
                    long startTime = System.currentTimeMillis();

                    // Get transcription from wav file
                    String result = mTFLiteEngine.getTranscription(mWavFile);

                    // Display output result
                    mUpdateListener.onStatusChanged(result);
                    long endTime = System.currentTimeMillis();
                    long timeTaken = endTime - startTime;
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                    Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);
                } else {
                    mUpdateListener.onStatusChanged(mContext.getString(R.string.input_file_doesn_t_exist));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error..", e);
            mUpdateListener.onStatusChanged(e.getMessage());
        } finally {
            mInProgress.set(false);
        }
    }
}