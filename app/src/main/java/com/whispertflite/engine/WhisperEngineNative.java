package com.whispertflite.engine;

import android.util.Log;
import com.whispertflite.asr.IWhisperListener;
import java.io.IOException;

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
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        int ret = loadModels(nativePtr, modelPath, vocabPath, multilingual);
        if (ret == 0) {
            Log.d(TAG, "Models are loaded... Model: " + modelPath + ", Vocab: " + vocabPath);
            mIsInitialized = true;
            return true;
        } else {
            Log.e(TAG, "Failed to load models");
            throw new IOException("Failed to load models");
        }
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
    public float[] encode(float[] audioBuffer) {
        return encodeAudio(nativePtr, audioBuffer);
    }

    @Override
    public String decode(float[] encoderOutput) {
        return decodeOutput(nativePtr, encoderOutput);
    }

    @Override
    public String streamingDecode(float[] encoderOutput) {
        return streamingDecodeOutput(nativePtr, encoderOutput);
    }

    @Override
    public void interrupt() {
        interruptProcessing(nativePtr);
    }

    public void updateStatus(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdateReceived(message);
    }

    @Override
    protected void finalize() throws Throwable {
        freeModels(nativePtr);
        super.finalize();
    }

    static {
        System.loadLibrary("audioEngine");
    }

    // Native methods
    private native long createTFLiteEngine();
    private native int loadModels(long nativePtr, String modelPath, String vocabPath, boolean isMultilingual);
    private native void freeModels(long nativePtr);
    private native String transcribeBuffer(long nativePtr, float[] samples);
    private native String transcribeFile(long nativePtr, String waveFile);
    private native float[] encodeAudio(long nativePtr, float[] audioBuffer);
    private native String decodeOutput(long nativePtr, float[] encoderOutput);
    private native String streamingDecodeOutput(long nativePtr, float[] encoderOutput);
    private native void interruptProcessing(long nativePtr);
}