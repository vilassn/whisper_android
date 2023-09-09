package com.example.tfliteaudio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";

    private Button btnMicRec;
    private Button btnTranscb;
    private TextView tvResult;
    private Handler mHandler;
    private String mSelectedFile;
    private RecordingThread mRecordingThread = null;
    private TranscriptionThread mTranscriptionThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_one);
        mHandler = new Handler(Looper.getMainLooper());
        tvResult = findViewById(R.id.tvResult);

        // Implementation of transcribe button functionality
        btnTranscb = findViewById(R.id.btnTranscb);
        btnTranscb.setOnClickListener(v -> {
            if (RecordingThread.isRecordingInProgress()) {
                stopRecording();
            }

            if (!TranscriptionThread.isTranscriptionInProgress()) {
                startTranscription();
            } else {
                Log.d(TAG, "Transcription is already in progress...!");
            }
        });

        // Implementation of record button functionality
        btnMicRec = findViewById(R.id.btnMicRecord);
        btnMicRec.setOnClickListener(v -> {
            if (!RecordingThread.isRecordingInProgress()) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        // Implementation of file spinner functionality
        ArrayList<String> files = new ArrayList<>();
        // files.add(WaveUtil.RECORDING_FILE);
        try {
            String[] assetFiles = getAssets().list("");
            for (String file : assetFiles) {
                if (file.endsWith(".wav"))
                    files.add(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] fileArray = new String[files.size()];
        files.toArray(fileArray);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = findViewById(R.id.spnrFiles);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedFile = fileArray[position];
                if (mSelectedFile.equals(WaveUtil.RECORDING_FILE))
                    btnMicRec.setVisibility(View.VISIBLE);
                else
                    btnMicRec.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Call the method to copy specific file types from assets to data folder
        String[] extensionsToCopy = {"pcm", "bin", "wav", "tflite"};
        copyAssetsWithExtensionsToDataFolder(this, extensionsToCopy);

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission();
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Requesting record permission");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "Record permission is granted");
        else
            Log.d(TAG, "Record permission is not granted");
    }

    private void startRecording() {
        checkRecordPermission();

        mRecordingThread = new RecordingThread(this);
        mRecordingThread.setRecordingInProgress(true);
        mRecordingThread.start();

        mHandler.post(() -> btnMicRec.setText(getString(R.string.stop)));
    }

    private void stopRecording() {
        try {
            if (mRecordingThread != null) {
                mRecordingThread.setRecordingInProgress(false);
                mRecordingThread.join();
                mRecordingThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mHandler.post(() -> btnMicRec.setText(getString(R.string.record)));
    }

    private void startTranscription() {
        mTranscriptionThread = new TranscriptionThread(this);
        mTranscriptionThread.setInputFile(mSelectedFile);
        mTranscriptionThread.start();
        mTranscriptionThread.setTranscriptionInProgress(true);
        //mTranscriptionThread.join();
    }

    public void updateUIStatus(final String message) {
        mHandler.post(() -> tvResult.setText(message));

        if(message.equals(getString(R.string.recording_is_completed)))
            mHandler.post(() -> btnMicRec.setText(getString(R.string.record)));
    }

    public static void copyAssetsWithExtensionsToDataFolder(Context context, String[] extensions) {
        AssetManager assetManager = context.getAssets();

        try {
            // Specify the destination directory in the app's data folder
            String destFolder = context.getFilesDir().getAbsolutePath();

            for (String extension : extensions) {
                // List all files in the assets folder with the specified extension
                String[] assetFiles = assetManager.list("");
                for (String assetFileName : assetFiles) {
                    if (assetFileName.endsWith("." + extension)) {
                        InputStream inputStream = assetManager.open(assetFileName);
                        File outFile = new File(destFolder, assetFileName);
                        OutputStream outputStream = new FileOutputStream(outFile);

                        // Copy the file from assets to the data folder
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }

                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}