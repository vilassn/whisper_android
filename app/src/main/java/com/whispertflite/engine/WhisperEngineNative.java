package com.whispertflite.engine;

import android.util.Log;

import com.whispertflite.asr.IWhisperListener;

public class WhisperEngineNative implements IWhisperEngine {
    private final String TAG = "WhisperEngineNative";
    private final long nativePtr; // Native pointer to the TFLiteEngine instance

    private boolean mIsInitialized = false;
    private IWhisperListener mUpdateListener = null;

    public WhisperEngineNative() {
        nativePtr = createTFLiteEngine();
    }

    @Override
    public void setUpdateListener(IWhisperListener listener) {
        mUpdateListener = listener;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) {
        int ret = loadModel(modelPath, multilingual);
        Log.d(TAG, "Model is loaded..." + modelPath);

        mIsInitialized = true;
        return true;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return transcribeBuffer(nativePtr, samples);
    }

    @Override
    public String transcribeFile(String waveFile) {
        return transcribeFile(nativePtr, waveFile);
    }

    @Override
    public void interrupt() {

    }

    public void updateStatus(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdateReceived(message);
    }

    private int loadModel(String modelPath, boolean isMultilingual) {
        return loadModel(nativePtr, modelPath, isMultilingual);
    }

    private void freeModel() {
        freeModel(nativePtr);
    }

    static {
        System.loadLibrary("audioEngine");
    }

    // Native methods
    private native long createTFLiteEngine();
    private native int loadModel(long nativePtr, String modelPath, boolean isMultilingual);
    private native void freeModel(long nativePtr);
    private native String transcribeBuffer(long nativePtr, float[] samples);
    private native String transcribeFile(long nativePtr, String waveFile);
}
