# Offline Speech Recognition App with OpenAI Whisper and TensorFlow Lite
Whisper is an automatic speech recognition (ASR) system trained on a massive dataset of 680,000 hours of multilingual and multitask supervised data collected from the web. This Android app allows you to leverage the power of OpenAI's Whisper ASR on your Android device using the TFLite (TensorFlow Lite) framework.

## Integration Guide

Follow these steps to seamlessly integrate the Whisper ASR functionality into your Android project:

**User Documentation for Whisper Class**

**Introduction**

The `Whisper` class is a utility class designed to interact with the Whisper ASR (Automatic Speech Recognition) engine for transcription and translation of audio data. This class provides a high-level interface to perform transcription and translation tasks using the Whisper engine.

**Usage**

To use the `Whisper` class in your Android application, follow the steps below:

1. **Initialization:**

   Create an instance of the `Whisper` class by passing the required parameters to the constructor:

   - `Context context`: A valid Android `Context` object.
   - `String modelPath`: The path to the Whisper model file.
   - `String vocabPath`: The path to the vocabulary file.
   - `boolean isMultilingual`: A flag indicating whether the engine supports multilingual capabilities.

   ```java
   Context context = ...; // Obtain a valid context
   String modelPath = ...; // Specify the model file path
   String vocabPath = ...; // Specify the vocabulary file path
   boolean isMultilingual = ...; // Specify whether the engine is multilingual
   Whisper whisper = new Whisper(context, modelPath, vocabPath, isMultilingual);
   ```

2. **Update Status Listener:**

   You can optionally set an update listener to receive status updates during the execution of Whisper tasks. Implement the `IUpdateListener` interface to handle status change callbacks.

   ```java
   whisper.setUpdateListener(new IUpdateListener() {
       @Override
       public void onStatusChanged(String message) {
           // Handle status change messages
       }
   });
   ```

3. **Setting Action:**

   Set the action you want to perform using the `setAction` method. Available actions are:
   
   - `ACTION_TRANSLATE`: Perform translation.
   - `ACTION_TRANSCRIBE_NATIVE`: Perform transcription using the native engine.
   - `ACTION_TRANSCRIBE_JAVA` (Default): Perform transcription using the Java engine.

   ```java
   whisper.setAction(Whisper.ACTION_TRANSLATE); // Set the desired action
   ```

4. **Setting File Path:**

   Set the path to the audio WAV file you want to process using the `setFilePath` method. Ensure that you have appropriate permissions to access the specified file.

   ```java
   String wavFilePath = ...; // Specify the audio WAV file path
   whisper.setFilePath(wavFilePath);
   ```

5. **Starting Execution:**

   To start the execution of the chosen action, call the `start` method. The execution process will continue until it is manually stopped using the `stop` method.

   ```java
   whisper.start();
   ```

6. **Stopping Execution:**

   To stop the execution, call the `stop` method. This will terminate the ongoing task.

   ```java
   whisper.stop();
   ```

7. **Checking Execution Status:**

   You can check whether the execution is currently in progress by calling the `isInProgress` method.

   ```java
   boolean isExecuting = whisper.isInProgress();
   ```

**Actions and Engines**

The `Whisper` class provides different actions and engine implementations:

- For transcription tasks, it offers both native and Java-based engines.
- For translation tasks, it provides translation engine support.

Select the desired action and engine by setting the action using `setAction`.

**Status Updates**

The `Whisper` class provides status updates during task execution through the registered update listener. Utilize this feature to display progress or messages to the user.

**Error Handling**

The class includes error handling to manage exceptions that may occur during engine initialization and task execution. Ensure proper exception handling in your application when using this class.

**Sample Usage**

Below is a sample code snippet demonstrating how to use the `Whisper` class:

```java
Context context = ...; // Obtain a valid context
String modelPath = ...; // Specify the model file path
String vocabPath = ...; // Specify the vocabulary file path
boolean isMultilingual = ...; // Specify whether the engine is multilingual

Whisper whisper = new Whisper(context, modelPath, vocabPath, isMultilingual);
whisper.setUpdateListener(new IUpdateListener() {
    @Override
    public void onStatusChanged(String message) {
        // Handle status change messages
    }
});
whisper.setAction(Whisper.ACTION_TRANSLATE); // Set the desired action
String wavFilePath = ...; // Specify the audio WAV file path
whisper.setFilePath(wavFilePath);
whisper.start();
// ... Perform other tasks or user interactions ...
whisper.stop();
```

**Note**: Ensure that you have the necessary permissions, error handling, and file path management in your application when using the `Whisper` class.





**User Documentation for Recorder Class**

**Introduction**

The `Recorder` class is a utility class designed for recording audio from a device's microphone and saving it to a WAV file. This class is primarily used to capture audio data for various purposes, such as speech recognition or audio transcription.

**Usage**

To use the `Recorder` class in your Android application, follow the steps below:

1. **Initialization:**

   Create an instance of the `Recorder` class by passing a valid `Context` object to the constructor. This context is used for permission checking.

   ```java
   Context context = ...; // Obtain a valid context
   Recorder recorder = new Recorder(context);
   ```

2. **Update Status Listener:**

   You can optionally set an update listener to receive status updates during the recording process. Implement the `IUpdateListener` interface to handle status change callbacks.

   ```java
   recorder.setUpdateListener(new IUpdateListener() {
       @Override
       public void onStatusChanged(String message) {
           // Handle status change messages
       }
   });
   ```

3. **Setting File Path:**

   Set the path where the recorded WAV file should be saved using the `setFilePath` method. Ensure that you have appropriate permissions to write to the specified file path.

   ```java
   String wavFilePath = ...; // Specify the file path
   recorder.setFilePath(wavFilePath);
   ```

4. **Starting Recording:**

   To start recording audio, call the `start` method. The recording process will continue until it is manually stopped using the `stop` method.

   ```java
   recorder.start();
   ```

5. **Stopping Recording:**

   To stop the recording, call the `stop` method. This will terminate the recording process and save the recorded audio to the specified file path.

   ```java
   recorder.stop();
   ```

6. **Checking Recording Status:**

   You can check whether the recording is currently in progress by calling the `isInProgress` method.

   ```java
   boolean isRecording = recorder.isInProgress();
   ```

**Recording Configuration**

The `Recorder` class uses the following configuration parameters for audio recording:

- **Sample Rate**: 16,000 Hz
- **Channels**: Mono (1 channel)
- **Bytes Per Sample**: 16-bit
- **Audio Source**: Microphone (MIC)

These parameters are suitable for many audio recording scenarios. You can modify these settings by adjusting the corresponding variables in the class if needed.

**Permissions**

Ensure that your Android application has the necessary permissions to record audio. The class checks for the `RECORD_AUDIO` permission before starting the recording. Make sure to request and handle this permission in your app's manifest and runtime permissions.

**Status Updates**

The `Recorder` class provides status updates during recording through the registered update listener. Use this feature to display progress or messages to the user.

**Error Handling**

The class includes error handling to handle various exceptions that may occur during audio recording and WAV file creation. Ensure proper exception handling in your application when using this class.

**Sample Usage**

Below is a sample code snippet demonstrating how to use the `Recorder` class:

```java
Context context = ...; // Obtain a valid context
Recorder recorder = new Recorder(context);
recorder.setUpdateListener(new IUpdateListener() {
    @Override
    public void onStatusChanged(String message) {
        // Handle status change messages
    }
});
String wavFilePath = ...; // Specify the file path
recorder.setFilePath(wavFilePath);
recorder.start();
// ... Perform other tasks or user interactions ...
recorder.stop();
```

**Note**: Ensure that you have the necessary permissions, error handling, and file path management in your application when using the `Recorder` class.

## Important Note

Whisper ASR is a powerful tool for transcribing speech into text. However, keep in mind that handling audio data and transcriptions may require careful synchronization and error handling in your Android application to ensure a smooth user experience.

Enjoy using the Whisper ASR Android app to enhance your speech recognition capabilities!
