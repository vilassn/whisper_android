package com.whispertflite.engine;

import com.whispertflite.asr.IUpdateListener;

public class WhisperEngineNative implements IWhisperEngine {
    private final String TAG = "WhisperEngineNative";
    private final long nativePtr; // Native pointer to the TFLiteEngine instance

    private boolean mIsInitialized = false;
    private IUpdateListener mUpdateListener = null;

    public WhisperEngineNative() {
        nativePtr = createTFLiteEngine();
    }

    @Override
    public boolean initialize(boolean multilingual, String vocabPath, String modelPath) {
        int ret = loadModel(modelPath, multilingual);
        mIsInitialized = true;
        return mIsInitialized;
    }

    public void updateStatus(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onStatusChanged(message);
    }

    public void setUpdateListener(IUpdateListener listener) {
        mUpdateListener = listener;
    }
    @Override
    public String getTranscription(String wavePath) {
        return transcribeFile(wavePath);
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public void interrupt() {

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

    private int loadModel(String modelPath, boolean isMultilingual) {
        return loadModel(nativePtr, modelPath, isMultilingual);
    }

    public String transcribeBuffer(float[] samples) {
        return transcribeBuffer(nativePtr, samples);
    }

    public String transcribeFile(String waveFile) {
        return transcribeFile(nativePtr, waveFile);
    }

    public void freeModel() {
        freeModel(nativePtr);
    }
}
