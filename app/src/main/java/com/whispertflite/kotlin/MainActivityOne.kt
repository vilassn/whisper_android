package com.whispertflite.kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.whispertflite.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MainActivityOne : AppCompatActivity() {

    private val TAG = "MainActivity"

    private var btnMicRec: Button? = null
    private var btnTranscb: Button? = null
    private var tvResult: TextView? = null
    private var mHandler: Handler? = null
    private var mSelectedFile: String? = null
    private var mAudioRecord: AudioRecord? = null
    private var mRecordingThread: Thread? = null
    private var mTranscriptionThread: Thread? = null

    private val mRecordedFile = "MicInput.wav"
    private val mBufferSize = WaveUtil.BUFFER_SIZE_30_SEC
    private val mTFLiteEngine = TFLiteEngineOne()
    private val mRecordingInProgress = AtomicBoolean(false)
    private val mTranscriptionInProgress = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main_kotlin_one)
        mHandler = Handler(Looper.getMainLooper())
        tvResult = findViewById(R.id.tvResult)

        // Implementation of transcribe button functionality
        btnTranscb = findViewById(R.id.btnTranscb)
        btnTranscb!!.setOnClickListener {
            if (mRecordingInProgress.get())
                stopRecording()

            if (!mTranscriptionInProgress.get())
                startTranscription()
            else
                Log.d(TAG, "Transcription is already in progress...!")
        }

        // Implementation of record button functionality
        btnMicRec = findViewById(R.id.btnMicRecord)
        btnMicRec!!.setOnClickListener {
            if (!mRecordingInProgress.get())
                startRecording()
            else
                stopRecording()
        }

        // Implementation of file spinner functionality
        val files = ArrayList<String>()
        files.add(mRecordedFile)
        try {
            for (file in assets.list("")!!) {
                if (file.endsWith(".wav"))
                    files.add(file)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val fileArray = arrayOfNulls<String>(files.size)
        files.toArray(fileArray)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fileArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinner = findViewById<Spinner>(R.id.spnrFiles)
        spinner.adapter = adapter
        //spinner.setSelection(0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                mSelectedFile = fileArray[position]
                if (mSelectedFile == mRecordedFile)
                    btnMicRec!!.visibility = View.VISIBLE
                else
                    btnMicRec!!.visibility = View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        // Assume this Activity is the current activity, check record permission
        checkRecordPermission()
    }

    private fun checkRecordPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is grated")
        } else {
            Log.d(TAG, "Requesting for record permission")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "Record permission is grated")
        else
            Log.d(TAG, "Record permission is not grated")
    }

    private fun startRecording() {
        checkRecordPermission()

        mAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            WaveUtil.SAMPLE_RATE,
            WaveUtil.CHANNEL_CONFIG,
            WaveUtil.AUDIO_FORMAT,
            mBufferSize
        )
        mAudioRecord!!.startRecording()
        mRecordingInProgress.set(true)
        mRecordingThread = Thread(RecordingRunnable(), "Recording Thread")
        mRecordingThread!!.start()
        mHandler!!.post { btnMicRec!!.text = getString(R.string.stop) }
    }

    private fun stopRecording() {
        if (mAudioRecord == null)
            return

        mRecordingInProgress.set(false)
        mAudioRecord!!.stop()
        mAudioRecord!!.release()
        mAudioRecord = null
        mRecordingThread!!.join()
        mRecordingThread = null
        mHandler!!.post { btnMicRec!!.text = getString(R.string.record) }
    }

    private fun startTranscription() {
        mTranscriptionInProgress.set(true)
        mTranscriptionThread = Thread(TranscriptionRunnable(), "Transcription Thread")
        mTranscriptionThread!!.start()
    }

    // Copies specified asset to app's files directory and returns its absolute path.
    @Throws(IOException::class)
    fun assetFilePath(assetName: String?): String {
        val outfile = File(filesDir, assetName.toString())
        if (!outfile.exists() || outfile.length() < 0) {
            var inStream: InputStream? = null
            var outStream: OutputStream? = null
            try {
                inStream = assets.open(assetName!!)
                outStream = FileOutputStream(outfile)
                val buffer = ByteArray(1024)
                var length: Int
                while (inStream.read(buffer).also { length = it } > 0) {
                    outStream.write(buffer, 0, length)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error...", e)
            } finally {
                inStream?.close()
                outStream?.close()
            }
        }

        Log.d(TAG, "Returned asset path: " + outfile.absolutePath)
        return outfile.absolutePath
    }

    private inner class RecordingRunnable : Runnable {
        override fun run() {
            try {
                val buffer = ByteBuffer.allocateDirect(mBufferSize)
                if (mRecordingInProgress.get()) {
                    val recordMsg = "Recording audio for 30 sec..."
                    mHandler!!.post { tvResult!!.text = recordMsg }
                    Log.d(TAG, recordMsg)

                    val result = mAudioRecord!!.read(buffer, mBufferSize)
                    if (result < 0) {
                        Log.d(TAG, "AudioRecord read error!!!")
                    }
                }

                Thread {
                    if (mRecordingInProgress.get())
                        stopRecording()
                }.start()

                // Write samples to wav file
                val samples = WaveUtil.getSamples(buffer.array(), WaveUtil.BYTES_PER_SAMPLE)
                val wavePath = filesDir.toString() + File.separator + mRecordedFile
                WaveUtil.createWaveFile(wavePath, samples)
                Log.d(TAG, "Recorded file: $wavePath")
                mHandler!!.post { tvResult!!.text = getString(R.string.recording_is_completed) }
            } catch (e: Exception) {
                throw RuntimeException("Writing of recorded audio failed", e)
            }
        }
    }

    private inner class TranscriptionRunnable : Runnable {
        override fun run() {
            try {
                // Initialize TFLiteEngine
                if (!mTFLiteEngine.isInitialized()) {
                    // Update progress to UI thread
                    mHandler!!.post { tvResult!!.text = getString(R.string.loading_model_and_vocab) }

                    // set true for multilingual support
                    // whisper.tflite => not multilingual
                    // whisper-small.tflite => multilingual
                    // whisper-tiny.tflite => multilingual
                    val isMultilingual = true

                    // Get Model and vocab file paths
                    val modelPath: String
                    val vocabPath: String
                    if(isMultilingual) {
                        modelPath = assetFilePath("whisper-tiny.tflite")
                        vocabPath = assetFilePath("filters_vocab_multilingual.bin")
                    } else {
                        modelPath = assetFilePath("whisper.tflite")
                        vocabPath = assetFilePath("filters_vocab_gen.bin")
                    }

                   mTFLiteEngine.initialize(isMultilingual, vocabPath, modelPath)
                }

                // Get Transcription
                if (mTFLiteEngine.isInitialized()) {
                    val wavePath = assetFilePath(mSelectedFile)
                    Log.d(TAG, "WaveFile: $wavePath")

                    if (File(wavePath).exists()) {
                        // Update progress to UI thread
                        mHandler!!.post { tvResult!!.text = getString(R.string.transcribing) }
                        val startTime = System.currentTimeMillis()

                        // Get transcription from wav file
                        val result = mTFLiteEngine.getTranscription(wavePath)

                        // Display output result
                        mHandler!!.post { tvResult!!.text = result }
                        val endTime = System.currentTimeMillis()
                        val timeTaken =  endTime - startTime
                        Log.d(TAG, "Time Taken for transcription: $timeTaken" + "ms")
                        Log.d(TAG, "Result len: " + result.length + ", Result: " + result)
                    } else {
                        mHandler!!.post {tvResult!!.text = getString(R.string.input_file_doesn_t_exist)}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Error..", e)
                mHandler!!.post {tvResult!!.text = e.message}
            } finally {
                mTranscriptionInProgress.set(false)
            }
        }
    }
}