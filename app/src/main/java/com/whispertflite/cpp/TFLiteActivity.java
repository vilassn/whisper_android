/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.whispertflite.cpp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.whispertflite.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample that demonstrates how to record a device's microphone using {@link AudioRecord}.
 */
public class TFLiteActivity extends AppCompatActivity {

    private final String TAG = "TFLiteActivity";

    private static final int SAMPLING_RATE_IN_HZ = 16000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by {@link AudioRecord#getMinBufferSize(int, int, int)} and depends on the
     * recording settings.
     */
    private static final int BUFFER_SIZE_FACTOR = 25 * 3;

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;

    /**
     * Signals whether a recording is in progress (true) or not (false).
     */
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);

    private AudioRecord recorder = null;

    private Thread recordingThread = null;

    private Button startButton;

    private Button stopButton;

    private Button transButton;
    private EditText transEditText;
    private TextView statusTextView;

    private Context mContext;
    private final TFLiteEngine mTFLiteEngine = new TFLiteEngine();
    private boolean isModelLoaded = false;

    private int AUDIO_RECORD_REQUEST = 12446;
    private final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mContext = this;
        setContentView(R.layout.activity_main_cpp_record);

        startButton = findViewById(R.id.btnStart);
        startButton.setOnClickListener(v -> {
            startRecording();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        stopButton = findViewById(R.id.btnStop);
        stopButton.setOnClickListener(v -> {
            stopRecording();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        transButton = findViewById(R.id.btnTrans);
        transButton.setOnClickListener(v -> transcribeFile());

        transEditText = findViewById(R.id.tvTranscb);
        statusTextView = findViewById(R.id.tvStatus);

        if(!isModelLoaded) {
            String modelPath = getFilePath("whisper-tiny.tflite");
            String vocabPath = getFilePath("filters_vocab_multilingual.bin");
            mTFLiteEngine.loadModel(modelPath, true);
            isModelLoaded = true;
        }

        checkRecordAudioPermission();
    }

    private String getFilePath(String assetName) {
        File outfile = new File(mContext.getFilesDir(), assetName);
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.getAbsolutePath());
        }

        Log.d(TAG, "Returned asset path: " + outfile.getAbsolutePath());
        return outfile.getAbsolutePath();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        //startButton.setEnabled(true);
        //stopButton.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        //stopRecording();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        // commented to avoid crash on screen rotation
        if(isModelLoaded) {
            //mAudioEngine.freeModel();
            //mAudioEngine.delete();
            //isModelLoaded = false;
        }
    }

    private void transcribeFile() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);

                String fileName = null;
                String[] list = getAssets().list("");
                Log.d(TAG, "Assets: " + Arrays.toString(list));
                //for (int i=0; i < list.length; i++) // uncomment to transcribe all files
                {
                    fileName = list[3]; // Bili file
                    //fileName =  list[i]; // uncomment to transcribe all files
//                    if (fileName.toLowerCase().endsWith(".wav")) {
                        Log.d(TAG, "Transcribing " + fileName);
                        String waveFile = getFilePath("MicInput.wav");
                        String transcription = mTFLiteEngine.transcribeFile(waveFile);
                        Log.d(TAG, transcription);
//                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No RECORD_AUDIO permission...!");
            statusTextView.setText("Please allow Microphone permission in App info");
            return;
        }

        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
        recorder.startRecording();
        recordingInProgress.set(true);
        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    private void stopRecording() {
        if (null == recorder) {
            return;
        }
        recordingInProgress.set(false);
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;
        statusTextView.setText("Idle");
    }


    private boolean checkRecordAudioPermission() {
        Log.d(TAG, "checkRecordAudioPermission: ");

        boolean isRecordingAllowed = isRecordPermissionGranted();
        Log.i(TAG, "checkRecordAudioPermission:" + isRecordingAllowed);

        if (!isRecordingAllowed) {
            requestRecordPermission();
        }

        return isRecordingAllowed;
    }

    private boolean isRecordPermissionGranted() {
        boolean permissionStatus =
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        Log.d(TAG, "isRecordPermissionGranted: " + permissionStatus);
        return permissionStatus;
    }

    private void requestRecordPermission() {
        Log.d(TAG, "requestRecordPermission: ");
        ActivityCompat.requestPermissions(this, PERMISSIONS, AUDIO_RECORD_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: requestCode: " + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (AUDIO_RECORD_REQUEST != requestCode) {
            return;
        }

        if (!isRecordPermissionGranted()) {
            Log.w(TAG, "onRequestPermissionsResult: Some Permission(s) not granted, disable controls");
        } else {
            Log.i(TAG, "onRequestPermissionsResult: ALL Permissions granted, continue with enableControls");
        }
    }

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {
//            File pcmFile;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                pcmFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/" + "recording.pcm");
//            } else {
//                pcmFile = new File(Environment.getExternalStorageDirectory(), "recording.pcm");
//            }
//            Log.d(TAG, pcmFile.getAbsolutePath());

            Log.d(TAG, "audioFormat: " + recorder.getAudioFormat());
            Log.d(TAG, "channelCount: " + recorder.getChannelCount());
            Log.d(TAG, "sampleRate: " + recorder.getSampleRate());

            try {
                String newTrans = "";
                String oldTrans = "";
                final ByteBuffer newbuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
                final ByteBuffer oldbuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
                final ByteBuffer mergedbuf = ByteBuffer.allocateDirect(BUFFER_SIZE * 2);
                //final FileOutputStream outStream = new FileOutputStream(pcmFile);
                while (recordingInProgress.get()) {
                    Log.d(TAG, "Capturing Audio..." + (newbuf.limit() / 4) / recorder.getSampleRate() + "sec");
                    statusTextView.setText("Capturing...");

                    //recorder.startRecording();
                    int result = recorder.read(newbuf, BUFFER_SIZE);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }

                    //if (recordingInProgress.get())
                    //  recorder.stop();

                    Log.d(TAG, "Processing Audio...");
                    statusTextView.setText("Processing...");

                    //newTrans = mAudioEngine.transcibeBuffer(newbuf.array(), newbuf.limit());
                    System.arraycopy(oldbuf.array(), 0, mergedbuf.array(), 0, oldbuf.limit());
                    System.arraycopy(newbuf.array(), 0, mergedbuf.array(), oldbuf.limit(), newbuf.limit());
                    //newTrans = mTFLiteEngine.transcibeBuffer(mergedbuf.array(), mergedbuf.limit());

                    Log.d(TAG, "newTrans: " + newTrans);
                    //Log.d(TAG, "oldTrans: " + oldTrans);
                    //newTrans = newTrans.replace(oldTrans.replace(".", ""), "");
                    //Log.d(TAG, "new - old: " + newTrans);

                    String finalTrans = newTrans;
                    ((TFLiteActivity) mContext).runOnUiThread(() -> transEditText.append(finalTrans));

                    //outStream.write(newbuf.array(), 0, BUFFER_SIZE);
                    oldTrans = newTrans;
                    System.arraycopy(newbuf.array(), 0, oldbuf.array(), 0, newbuf.limit());
                    newbuf.clear();
                }
            } catch (Exception e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }
}