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
import android.view.ViewGroup
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



class MainActivityTwo : AppCompatActivity() {

    private val TAG = "MainActivity"

    private var btnMicRec: Button? = null
    private var btnTranscb: Button? = null
    private var btnTranslate: Button? = null
    private var tvResult: TextView? = null
    private var mHandler: Handler? = null
    private var mSelectedFile: String? = null
    private var mAudioRecord: AudioRecord? = null
    private var mRecordingThread: Thread? = null
    private var mTranscriptionThread: Thread? = null

    private val mRecordedFile = "MicInput"
    private val mBufferSize = WaveUtil.BUFFER_SIZE_30_SEC
    private val mTFLiteEngine = TFLiteEngineTwo()
    private val mRecordingInProgress = AtomicBoolean(false)
    private val mTranscriptionInProgress = AtomicBoolean(false)

    // Define the input speech language (English 50259, Spanish 50262, Hindi 50276)
    private var mInputLang: Long = 50259

    // Define the action to perform on the input (e.g., Transcribe)
    private var mAction: Long = Vocab.TRANSCRIBE.toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main_kotlin_two)
        mHandler = Handler(Looper.getMainLooper())
        tvResult = findViewById(R.id.tvResult)

        // Implementation of record button functionality
        btnMicRec = findViewById(R.id.btnMicRecord)
        btnMicRec!!.setOnClickListener {
            if (!mRecordingInProgress.get())
                startRecording()
            else
                stopRecording()
        }

        // Implementation of transcribe button functionality
        btnTranscb = findViewById(R.id.btnTranscb)
        btnTranscb!!.setOnClickListener {
            mAction = Vocab.TRANSCRIBE.toLong();
            if (mRecordingInProgress.get())
                stopRecording()

            if (!mTranscriptionInProgress.get())
                startTranscription()
            else
                Log.d(TAG, "Transcription is already in progress...!")
        }

        // Implementation of transcribe button functionality
        btnTranslate = findViewById(R.id.btnTranslate)
        btnTranslate!!.setOnClickListener {
            mAction = Vocab.TRANSLATE.toLong();
            if (mRecordingInProgress.get())
                stopRecording()

            if (!mTranscriptionInProgress.get())
                startTranscription()
            else
                Log.d(TAG, "Transcription is already in progress...!")
        }

        // Implementation of file spinner functionality
        val files = try {
            assets.list("")?.filter { it.endsWith(".wav") }?.toMutableList()
        } catch (e: IOException) {
            throw RuntimeException(e)
        } ?: mutableListOf()

        files.add(0, mRecordedFile)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, files)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinnerFiles = findViewById<Spinner>(R.id.spnrFiles)

        spinnerFiles.adapter = adapter
        spinnerFiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mSelectedFile = files[position]
                if (mSelectedFile == mRecordedFile)
                    btnMicRec!!.visibility = View.VISIBLE
                else
                    btnMicRec!!.visibility = View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerFiles.setSelection(0)

        // Initialize the list of input language objects
        val inputLangList = mutableListOf<InputLang>()
        inputLangList.add(InputLang("English", "en", 50259))
        inputLangList.add(InputLang("Spanish", "es",50262))
        inputLangList.add(InputLang("Hindi", "hi", 50276))
        inputLangList.add(InputLang("Telugu", "te",50299))

        val spinnerLangs = findViewById<Spinner>(R.id.spnrLangs)

        // Create a custom ArrayAdapter to display the language names
        val adapterLang = object : ArrayAdapter<InputLang>(this, android.R.layout.simple_spinner_item, inputLangList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                textView.text = inputLangList[position].name
                return textView
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getDropDownView(position, convertView, parent) as TextView
                textView.text = inputLangList[position].name

                // Add some padding between items in the dropdown list
                val paddingVertical = 16 // Adjust the value as needed
                textView.setPadding(0, paddingVertical, 0, paddingVertical)

                return textView
            }
        }

        // Set the adapter for the spinner
        spinnerLangs.adapter = adapterLang

        // Set a listener to handle item selection
        spinnerLangs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mInputLang = inputLangList[position].id
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerLangs.setSelection(0)

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
                // Transcribe using single model
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

                   mTFLiteEngine.initialize(assets, isMultilingual, vocabPath, modelPath)
                }

                // Get Transcription
                if (mTFLiteEngine.isInitialized()) {
                    val wavePath = assetFilePath(mSelectedFile)
                    Log.d(TAG, "WaveFile: $wavePath")

                    if (File(wavePath).exists()) {
                        // Update progress to UI thread
                        mHandler!!.post { tvResult!!.text = getString(R.string.processing) }
                        val startTime = System.currentTimeMillis()

                        // Get transcription from wav file
                        val result: String? = mTFLiteEngine.getTranscription(wavePath, mInputLang, mAction)

                        // Display output result
                        mHandler!!.post { tvResult!!.text = result }
                        val endTime = System.currentTimeMillis()
                        val timeTaken =  endTime - startTime
                        Log.d(TAG, "Time Taken for transcription: $timeTaken" + "ms")
                        Log.d(TAG, "Result len: " + result!!.length + ", Result: " + result)
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

    class InputLang(val name: String, val code: String, val id: Long)
}