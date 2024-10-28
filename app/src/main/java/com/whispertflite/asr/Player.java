package com.whispertflite.asr;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

public class Player {
    private static final String TAG = "Player";
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    private IAudioPlayerListener audioPlayerListener;

    public interface IAudioPlayerListener {
        void onPlaybackStarted();
        void onPlaybackStopped();
    }

    public void setAudioPlayerListener(IAudioPlayerListener listener) {
        this.audioPlayerListener = listener;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Sets up the file path for playback, but does not start playing immediately.
     * Call `startPlayback()` to start playing the audio.
     */
    public void setFilePath(String filePath) {
        if (isPlaying) {
            Log.d(TAG, "Playback already in progress. Stop the current playback before setting a new file.");
            return;
        }

        releasePlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source or preparing MediaPlayer: ", e);
            releasePlayer();
        }
    }

    /**
     * Starts playing the audio file set by `setFilePath`.
     */
    public void startPlayback() {
        if (mediaPlayer == null) {
            Log.d(TAG, "No file path set. Use `setFilePath` to set the file before starting playback.");
            return;
        }

        if (isPlaying) {
            Log.d(TAG, "Playback already in progress.");
            return;
        }

        mediaPlayer.start();
        isPlaying = true;

        // Notify listener that playback has started
        if (audioPlayerListener != null) {
            audioPlayerListener.onPlaybackStarted();
        }

        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            releasePlayer();
            Log.d(TAG, "Playback completed.");
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Playback error occurred: " + what + ", " + extra);
            releasePlayer();
            return true;
        });
    }

    /**
     * Stops playback if it is currently playing.
     */
    public void stopPlayback() {
        if (isPlaying && mediaPlayer != null) {
            mediaPlayer.stop();
            releasePlayer();
            isPlaying = false;
            Log.d(TAG, "Playback stopped.");
        }
    }

    /**
     * Releases the MediaPlayer resources.
     */
    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Notify listener that playback has stopped
        if (audioPlayerListener != null) {
            audioPlayerListener.onPlaybackStopped();
        }
    }
}
