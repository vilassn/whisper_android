package com.whispertflite.asr;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

public class Player {

    public interface PlaybackListener {
        void onPlaybackStarted();
        void onPlaybackStopped();
    }

    private MediaPlayer mediaPlayer;
    private PlaybackListener playbackListener;
    private final Context context;

    public Player(Context context) {
        this.context = context;
    }

    public void setListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    public void initializePlayer(String filePath) {
        Uri waveFileUri = Uri.parse(filePath);
        if (waveFileUri == null || context == null) {
            Log.e("WavePlayer", "File path or context is null. Cannot initialize MediaPlayer.");
            return;
        }

        releaseMediaPlayer(); // Release any existing MediaPlayer

        mediaPlayer = MediaPlayer.create(context, waveFileUri);
        if (mediaPlayer != null) {
            mediaPlayer.setOnPreparedListener(mp -> {
                if (playbackListener != null) {
                    playbackListener.onPlaybackStarted();
                }
                mediaPlayer.start();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                if (playbackListener != null) {
                    playbackListener.onPlaybackStopped();
                }
                releaseMediaPlayer();
            });
        } else {
            if (playbackListener != null) {
                playbackListener.onPlaybackStopped();
            }
        }
    }

    public void startPlayback() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            if (playbackListener != null) {
                playbackListener.onPlaybackStarted();
            }
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            if (playbackListener != null) {
                playbackListener.onPlaybackStopped();
            }
            releaseMediaPlayer();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
