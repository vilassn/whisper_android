package com.whispertflite.engine;

import com.whispertflite.asr.IWhisperListener;

import java.io.IOException;

public interface IWhisperEngine {
    boolean isInitialized();
    void interrupt();
    void setUpdateListener(IWhisperListener listener);
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    String transcribeFile(String wavePath);
    String transcribeBuffer(float[] samples);
    float[] encode(float[] audioBuffer);
    String decode(float[] encoderOutput);
    String streamingDecode(float[] encoderOutput);

    //String getTranslation(String wavePath);
}
