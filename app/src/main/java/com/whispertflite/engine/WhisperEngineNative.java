package com.whispertflite.engine;

import android.content.Context;
import android.util.Log;

public class WhisperEngineNative implements WhisperEngine {
    private final String TAG = "WhisperEngineNative";
    private final long nativePtr; // Native pointer to the TFLiteEngine instance

    private final Context mContext;
    private boolean mIsInitialized = false;

    public WhisperEngineNative(Context context) {
        mContext = context;
        nativePtr = createTFLiteEngine();
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
    public void deinitialize() {
        freeModel();
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return transcribeBuffer(nativePtr, samples);
    }

    @Override
    public String transcribeFile(String waveFile) {
        return transcribeFile(nativePtr, waveFile);
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
