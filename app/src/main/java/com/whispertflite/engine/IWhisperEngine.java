package com.whispertflite.engine;

import com.whispertflite.asr.IOnUpdateListener;

import java.io.IOException;

public interface IWhisperEngine {
    boolean isInitialized();
    void interrupt();
    void setUpdateListener(IOnUpdateListener listener);
    boolean initialize(boolean multilingual, String vocabPath, String modelPath) throws IOException;
    String getTranscription(String wavePath);
}
