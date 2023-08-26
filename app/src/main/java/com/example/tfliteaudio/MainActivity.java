package com.example.tfliteaudio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ASR";

    private Button btnMicRec;
    private Button btnTranscb;
    private TextView tvResult;
    private Handler mHandler;
    private String mSelectedFile;
    private AudioRecord mAudioRecord = null;
    private Thread mRecordingThread = null;
    private boolean mIsModelInitialized = false;
    private final TFLiteEngine mTFLiteEngine = new TFLiteEngine();
    private final AtomicBoolean mRecordingInProgress = new AtomicBoolean(false);

    public static String RECORDED_FILE = "Mic";
    public static final int SAMPLING_RATE = 16000;
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    public static final int BUFFER_SIZE = WaveUtil.getBufferSize30sec();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        tvResult = findViewById(R.id.tvResult);

        // Implementation of start button functionality
        btnTranscb = findViewById(R.id.btnTranscb);
        btnTranscb.setOnClickListener(v -> transcribeAudio());

        btnMicRec = findViewById(R.id.btnMicRecord);
        btnMicRec.setOnClickListener(v -> {
            if (!mRecordingInProgress.get())
                startRecording();
            else
                stopRecording();
        });

        // Implementation of file spinner functionality
        ArrayList<String> files = new ArrayList<>();
        files.add(RECORDED_FILE);
        try {
            for (String file : getAssets().list("")) {
                if (file.endsWith(".wav"))
                    files.add(file);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] fileArray = new String[files.size()];
        files.toArray(fileArray);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = findViewById(R.id.spinner);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedFile = fileArray[position];
                if (mSelectedFile.equals(RECORDED_FILE))
                    btnMicRec.setVisibility(View.VISIBLE);
                else
                    btnMicRec.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Assume this Activity is the current activity, check record permission
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            //this means permission is granted and you can do read and write
            System.out.println("Record permission is grated");
        } else {
            System.out.println("Requesting for record permission");
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    private void transcribeAudio() {
        // New thread to perform background operation
        new Thread(() -> {
            Log.d(TAG, "################### Transcribe start ####################");
            try {
                // Test code for wav file read write operation
//                String pcmFile = assetFilePath("eng1_1ch_16k_32float.pcm");
//                String wavePath = getFilesDir() + File.separator + "pcm_to_wav.wav";
//                WaveUtil.pcmToWaveFile(pcmFile, wavePath);

                // Model and vocab file paths
                String modelPath = assetFilePath("whisper.tflite");
                String vocabPath = assetFilePath("filters_vocab_gen.bin");

                // Initialize TFLiteEngine
                if (!mIsModelInitialized) {
                    mIsModelInitialized = mTFLiteEngine.initialize(vocabPath, modelPath);
                }

                if (mIsModelInitialized) {
                    // Get Transcription
                    String wavePath = assetFilePath(mSelectedFile);
                    Log.d(TAG, "WaveFile: " + wavePath);

                    if (new File(wavePath).exists()) {
                        // to copy mel bin to sdcard
//                        assetFilePath("mel_spectrogram.bin");
//
//                        int counter = 0;
//                        while (true) {
                            // Update progress to UI thread
                            mHandler.post(() -> tvResult.setText("Transcribing..."));

                            String result = mTFLiteEngine.getTranscription(wavePath);

                            // Display output result
                            mHandler.post(() -> tvResult.setText(result));
//                            Log.d(TAG, "counter: " + counter++);
                            Log.d(TAG, "Result len: " + result.length() + ", Result: " + result);

//                            if (result.length() != 441){
//                                mHandler.post(() -> tvResult.setText("Failed"));
//                                break;
//                            }
//                        }
                    } else {
                        mHandler.post(() -> tvResult.setText("Input file doesn't exist..!"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error..", e);
            }
            Log.d(TAG, "################### Transcribe end ####################");
        }).start();
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
        mAudioRecord.startRecording();
        mRecordingInProgress.set(true);
        mRecordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        mRecordingThread.start();

        mHandler.post(() -> btnMicRec.setText("Stop"));
    }

    private void stopRecording() {
        if (mAudioRecord == null)
            return;

        mRecordingInProgress.set(false);
        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;
        mRecordingThread = null;

        mHandler.post(() -> btnMicRec.setText("Record"));
    }

    // Copies specified asset to app's files directory and returns its absolute path.
    public String assetFilePath(String assetName) throws IOException {
        File outfile = new File(getFilesDir(), assetName);
        if (!outfile.exists() || outfile.length() < 0) {
            InputStream is = null;
            OutputStream os = null;
            try {
                //is = new FileInputStream(assetName);
                is = getAssets().open(assetName);
                os = new FileOutputStream(outfile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } catch (Exception e) {
                Log.e("TAG", "Error...", e);
            } finally {
                if (is != null)
                    is.close();

                if (os != null)
                    os.close();
            }
        }
        Log.d("TAG", "Returned asset path: " + outfile.getAbsolutePath());
        return outfile.getAbsolutePath();
    }

    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            try {
                String wavePath = getFilesDir() + File.separator + RECORDED_FILE;
                final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                if (mRecordingInProgress.get()) { //use while
                    int bufferSize1Sec = WaveUtil.getBufferSize1sec();
                    String recordMsg = "Recording audio..." + (BUFFER_SIZE / bufferSize1Sec) + "sec";
                    mHandler.post(() -> tvResult.setText(recordMsg));
                    Log.d(TAG, recordMsg);

                    //recorder.startRecording();
                    int result = mAudioRecord.read(buffer, BUFFER_SIZE);
                    if (result < 0) {
                        Log.d(TAG, "return error");
                    }
                }

                if (mRecordingInProgress.get())
                    stopRecording();

                float[] samples = WaveUtil.byteArrayToFloatArray(buffer.array());

                // Write samples to wav file
                WaveUtil.createWaveFile(wavePath, samples);
                Log.d(TAG, "Recorded file: " + wavePath);
                mHandler.post(() -> tvResult.setText("Recording is completed..!"));
            } catch (Exception e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }
    }
}