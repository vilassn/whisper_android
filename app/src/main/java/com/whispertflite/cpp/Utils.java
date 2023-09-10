package com.whispertflite.cpp;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class Utils {
    private static final String TAG = "Utils";

    public static String getAudioRecordingFilePath(Context context) {
        File imagePath = new File(context.getExternalFilesDir(null), "audio");
        if (!imagePath.exists()) {
            boolean directoryCreationStatus = imagePath.mkdirs();
            Log.i(TAG, "getAudioRecordingFilePath: directoryCreationStatus: " + directoryCreationStatus);
        }

        File newFile = new File(imagePath, "oboe_recording.wav");
        String filePath = newFile.getAbsolutePath();

        Log.d(TAG, "getAudioRecordingFilePath: filePath: $filePath, fileExists: ${newFile.exists()}");

        if (newFile.exists()) {
            boolean deletionStatus = newFile.delete();
            Log.i(TAG, "getAudioRecordingFilePath: File already exists, delete it first, deletionStatus: $deletionStatus");
        }

        if (!newFile.exists()) {
            boolean fileCreationStatus = false;
            try {
                fileCreationStatus = newFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i(TAG, "getAudioRecordingFilePath: fileCreationStatus: " + fileCreationStatus);
        }

        return filePath;
    }

    public static void showPermissionsErrorDialog(Context context) {
        
    }
}
