# Offline Speech Recognition App with OpenAI Whisper and TensorFlow Lite
Whisper is an automatic speech recognition (ASR) system trained on a massive dataset of 680,000 hours of multilingual and multitask supervised data collected from the web. This Android app allows you to leverage the power of OpenAI's Whisper ASR on your Android device using the TFLite (TensorFlow Lite) framework.

## Integration Guide

Follow these steps to seamlessly integrate the Whisper ASR functionality into your Android project:

### 1. Initialize the Whisper ASR Engine

Before using Whisper ASR, you need to initialize the ASR engine by loading the model and vocabulary. Use the `TFLiteEngine::initialize()` method with the following parameters:

```kotlin
val isMultilingual = true // Set to true for multilingual ASR
val vocabPath = "your_vocab_file.txt" // Provide the path to your vocabulary file
val modelPath = "your_model_file.tflite" // Provide the path to your TFLite model file

mTFLiteEngine.initialize(isMultilingual, vocabPath, modelPath)
```

### 2. Transcribe Audio

Once the ASR engine is initialized, you can start transcribing audio files. Use the `TFLiteEngine::getTranscription()` method, providing the path to the WAV audio file (16K, mono, float) that you want to transcribe. Here's how to retrieve the transcription:

```kotlin
val wavePath = "your_audio_file.wav" // Provide the path to your audio file
val result = mTFLiteEngine.getTranscription(wavePath)
```

The `result` variable will contain the transcribed text from the provided WAV audio file.

### 3. Avoid Reentrancy

Please note that it's essential to avoid reentrancy of the `getTranscription()` method when a previous transcription is still in progress. Ensure that you manage the flow of transcription requests in your application to prevent conflicts.

## Example Usage

Here's an example of how you can use the Whisper ASR engine in your Android app:

```kotlin
// Initialize the ASR engine
val isMultilingual = true
val vocabPath = "your_vocab_file.txt"
val modelPath = "your_model_file.tflite"
mTFLiteEngine.initialize(isMultilingual, vocabPath, modelPath)

// Transcribe an audio file
val wavePath = "your_audio_file.wav"
val result = mTFLiteEngine.getTranscription(wavePath)

// Handle the transcription result
textView.text = result
```

## Important Note

Whisper ASR is a powerful tool for transcribing speech into text. However, keep in mind that handling audio data and transcriptions may require careful synchronization and error handling in your Android application to ensure a smooth user experience.

Enjoy using the Whisper ASR Android app to enhance your speech recognition capabilities!
