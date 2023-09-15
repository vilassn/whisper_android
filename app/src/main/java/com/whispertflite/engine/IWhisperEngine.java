package com.whispertflite.engine;

import com.whispertflite.asr.IOnUpdateListener;

import java.io.IOException;

public interface IWhisperEngine {
    boolean isInitialized();
    void interrupt();
    void setUpdateListener(IOnUpdateListener listener);
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    String getTranscription(String wavePath);

    //String getTranslation(String wavePath);
}
