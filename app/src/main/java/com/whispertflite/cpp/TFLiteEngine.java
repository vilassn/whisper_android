package com.whispertflite.cpp;

public class TFLiteEngine {

        static {
            System.loadLibrary("audioEngine");
        }

        // Native methods
        private native long createTFLiteEngine();
        private native int loadModel(long nativePtr, String modelPath, boolean isMultilingual);
        private native void freeModel(long nativePtr);
        private native String transcribeBuffer(long nativePtr, float[] samples);
        private native String transcribeFile(long nativePtr, String waveFile);

        // Native pointer to the TFLiteEngine instance
        private long nativePtr;

        public TFLiteEngine() {
            nativePtr = createTFLiteEngine();
        }

        public int loadModel(String modelPath, boolean isMultilingual) {
            return loadModel(nativePtr, modelPath, isMultilingual);
        }

        public void freeModel() {
            freeModel(nativePtr);
        }

        public String transcribeBuffer(float[] samples) {
            return transcribeBuffer(nativePtr, samples);
        }

        public String transcribeFile(String waveFile) {
            return transcribeFile(nativePtr, waveFile);
        }

//        public static void main(String[] args) {
//            // Usage example
//            TFLiteEngine engine = new TFLiteEngine();
//            int result = engine.loadModel("model.tflite", true);
//            if (result == 0) {
//                float[] samples = /* Your audio samples */;
//                String transcription = engine.transcribeBuffer(samples);
//                System.out.println("Transcription: " + transcription);
//                engine.freeModel();
//            } else {
//                System.err.println("Failed to load model.");
//            }
//        }
}
