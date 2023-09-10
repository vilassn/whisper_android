package com.whispertflite.java;

import android.content.Context;
import android.util.Log;

import com.whispertflite.R;
import com.whispertflite.common.UpdateListener;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranscriptionThread extends Thread {
    private final String TAG = "TranscriptionThread";
    private final Context mContext;
    private final UpdateListener mUpdateListener;
    private String mInputWavFile;
    private final TFLiteEngine mTFLiteEngine = new TFLiteEngine();
    private static final AtomicBoolean mTranscriptionInProgress = new AtomicBoolean(false);

    public TranscriptionThread(MainActivity mainActivity) {
        mContext = mainActivity;
        mUpdateListener = mainActivity;
    }

    public void setTranscriptionInProgress(boolean value) {
        mTranscriptionInProgress.set(value);
    }

    public static boolean isTranscriptionInProgress() {
        return mTranscriptionInProgress.get();
    }

    public void setInputFile(String inputWavFile) {
        mInputWavFile = inputWavFile;
    }

    @Override
    public void run() {
        String TAG = "TranscriptionThread";
        try {
            // Initialize TFLiteEngine
            if (!mTFLiteEngine.isInitialized()) {
                // Update progress to UI thread
                mUpdateListener.updateStatus(mContext.getString(R.string.loading_model_and_vocab));

                // set true for multilingual support
                // whisper.tflite => not multilingual
                // whisper-small.tflite => multilingual
                // whisper-tiny.tflite => multilingual
                boolean isMultilingual = true;

                // Get Model and vocab file paths
                String modelPath;
                String vocabPath;
                if (isMultilingual) {
                    modelPath = getFilePath("whisper-tiny.tflite");
                    vocabPath = getFilePath("filters_vocab_multilingual.bin");
                } else {
                    modelPath = getFilePath("whisper-tiny-en.tflite");
                    vocabPath = getFilePath("filters_vocab_gen.bin");
                }

                mTFLiteEngine.initialize(isMultilingual, vocabPath, modelPath);
            }

            // Get Transcription
            if (mTFLiteEngine.isInitialized()) {
                String wavePath = getFilePath(mInputWavFile);
                Log.d(TAG, "WaveFile: " + wavePath);

                if (new File(wavePath).exists()) {
                    // Update progress to UI thread
                    mUpdateListener.updateStatus(mContext.getString(R.string.transcribing));
                    long startTime = System.currentTimeMillis();

                    // Get transcription from wav file
                    String result = mTFLiteEngine.getTranscription(wavePath);

                    // Display output result
                    mUpdateListener.updateStatus(result);
                    long endTime = System.currentTimeMillis();
                    long timeTaken = endTime - startTime;
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms");
                    Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);
                } else {
                    mUpdateListener.updateStatus(mContext.getString(R.string.input_file_doesn_t_exist));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error..", e);
            mUpdateListener.updateStatus(e.getMessage());
        } finally {
            mTranscriptionInProgress.set(false);
        }
    }

    // Copies specified asset to app's files directory and returns its absolute path.
    private String getFilePath(String assetName) {
        File outfile = new File(mContext.getFilesDir(), assetName);
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.getAbsolutePath());
        }

        Log.d(TAG, "Returned asset path: " + outfile.getAbsolutePath());
        return outfile.getAbsolutePath();
    }
}