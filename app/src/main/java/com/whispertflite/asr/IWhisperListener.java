package com.whispertflite.asr;

public interface IWhisperListener {
    void onUpdateReceived(String message);
    void onResultReceived(String result);
}
