package com.whispertflite.common;

import java.io.IOException;

public interface ITFLiteEngine {
    boolean initialize(boolean multilingual, String vocabPath, String modelPath) throws IOException;
    String getTranscription(String wavePath);

    boolean isInitialized();
}
