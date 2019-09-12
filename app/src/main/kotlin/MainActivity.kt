package com.example.gRPCTest


import android.Manifest.permission
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val STATE_RESULTS = "results"
const val COLOR_HEARING = "colorHearing"
const val COLOR_NOT_HEARING = "colorNotHearing"

class MainActivity : AppCompatActivity() {

    private val mRequestCodeRecord = 1
    private var mColorHearing = 0
    private var mColorNotHearing = 0
    private var mSpeechService: SpeechService? = null // given after SpeechService begun
    private var mVoiceRecorder: VoiceRecorder? = null // given after on Start and permission was granted
    private lateinit var vModel: MainViewModel
    private lateinit var mAdapter: ResultAdapter // initialized by on Create
    private lateinit var mSpeechServiceListener: SpeechService.Listener // initialized by on Create
    private lateinit var mServiceConnection: ServiceConnection // initialized by onCreate
    private lateinit var mVoiceCallback: VoiceRecorder.Callback // initialized by onCreate

    // Activity life Cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vModel = ViewModelProvider(this@MainActivity).get(MainViewModel::class.java)

        mColorHearing = getColor(R.color.status_hearing)
        mColorNotHearing = getColor(R.color.status_not_hearing)

        val result = savedInstanceState?.getStringArrayList(STATE_RESULTS)
            ?: arrayListOf("one", "two", "three", "four", "five")

        initSpeechProcessor()
        mAdapter = ResultAdapter(result,vModel)
        recyclerView.adapter = mAdapter
    }
    override fun onStart() {
        super.onStart()
        // Prepare Cloud Speech API
        val intent = Intent(this, SpeechService::class.java)
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)

        val audioPermission = checkSelfPermission(this.baseContext, permission.RECORD_AUDIO)
        when {
            audioPermission == PERMISSION_GRANTED -> {
                startVoiceRecorder()
            }
            shouldShowRequestPermissionRationale(permission.RECORD_AUDIO) -> {
                AlertDialog.Builder(this)
                    .setTitle("permission")
                    .setMessage("このアプリの利用には音声の録音を許可してください.")
                Log.w("test", "permission request was disabled")
            }
            else -> {
                Log.w("test", "this app has no permission yet.")
                ActivityCompat.requestPermissions(this, arrayOf(permission.RECORD_AUDIO), mRequestCodeRecord)
            }
        }
        vModel.isAudioRecording.observe(this, Observer<Boolean> { t ->
            if (t != null && t == true) audioRecorderStatus.setTextColor(mColorHearing)
            else audioRecorderStatus.setTextColor(mColorNotHearing)
        })
        vModel.isVoiceRecording.observe(this, Observer<Boolean> { t ->
            if (t != null && t == true) voiceRecorderStatus.setTextColor(mColorHearing)
            else voiceRecorderStatus.setTextColor(mColorNotHearing)
        })
        vModel.isRecognizing.observe(this, Observer<Boolean> { t ->
            if (t != null && t == true) recognizingStatus.setTextColor(mColorHearing)
            else recognizingStatus.setTextColor(mColorNotHearing)
        })

    }
    override fun onStop() {
        stopVoiceRecorder()
        mSpeechService?.removeListener(mSpeechServiceListener)
        unbindService(mServiceConnection)
        mSpeechService = null
        super.onStop()
    }

    override fun onSaveInstanceState(
        outState: Bundle?,
        outPersistentState: PersistableBundle?
    ) { // on Pauseや回転後 on Stop前
        super.onSaveInstanceState(outState, outPersistentState)
        outState?.apply {
            putStringArrayList(STATE_RESULTS, mAdapter.getResults())
            putInt(COLOR_HEARING, mColorHearing)
            putInt(COLOR_NOT_HEARING, mColorNotHearing)
        }

    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        val stringArray = savedInstanceState?.getStringArrayList(STATE_RESULTS)
        if (!stringArray.isNullOrEmpty()) mAdapter.upDateResultList(stringArray)
    }

    // Activity Event
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
            // AUDIO RECORD Permission Granted
            startVoiceRecorder()
        } else if (shouldShowRequestPermissionRationale(permission.RECORD_AUDIO)) {
            Log.w("test", "permission request was disabled")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } else {
            Log.w("test", "permission was refused by request")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
    // Private method
    private fun initSpeechProcessor() {
        mSpeechServiceListener = object : SpeechService.Listener {
            override fun onSpeechRecognized(text: String, isFinal: Boolean) {
                // text 現在までに認識されたお言葉｡ 　認識終了するとisFinal　= trueになる｡
                if (isFinal) {
                    mVoiceRecorder?.dismiss()
                    vModel.isVoiceRecording.postValue(false)
                    vModel.isRecognizing.postValue(false)
                    mAdapter.addResult(text)
                }
                if (text.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Default).launch {
                        vModel.isRecognizing.postValue(true)
                        vModel.recognizedChannel.send(text)
                    }
                }
            }
        }
        mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                mSpeechService = SpeechService().from(service)
                mSpeechService?.addListener(listener = mSpeechServiceListener)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                mSpeechService = null
            }
        }
        mVoiceCallback = object : VoiceRecorder.Callback { // VoiceRecorderの録音時イベントの実装
            override fun onVoiceStart() {
                val sampleRate = mVoiceRecorder?.getSampleRate()
                if (sampleRate != null && sampleRate != 0) {
                    mSpeechService?.startRecognizing(sampleRate)
                    vModel.isVoiceRecording.postValue(true)
                }
            }
            override fun onVoice(data: ByteArray, size: Int) {
                super.onVoice(data, size)
                mSpeechService?.recognize(data, size)
            }
            override fun onVoiceEnd() {
                mSpeechService?.finishRecognizing()

            }
        }
        startRecordingBtn.setOnClickListener {
            startVoiceRecorder()
            /*            if (mChannelJob == null) {
                            mChannelJob = CoroutineScope(Dispatchers.Main).launch {
                                val channelText = vModel.recognizedChannel.receive()
                                channelViewer.text = channelText
                            }
                        } else {
                            if (mChannelJob!!.isActive) {
                                mChannelJob!!.cancel()
                                return@setOnClickListener
                            }
                            else mChannelJob = CoroutineScope(Dispatchers.Main).launch {
                                val channelText = vModel.recognizedChannel.receive()
                                channelViewer.text = channelText
                            }
                        }*/
        }
        stopRecordingBtn.setOnClickListener {
            stopVoiceRecorder()
        }
    }

    private fun startVoiceRecorder() {
        mVoiceRecorder?.stop()
        mVoiceRecorder = VoiceRecorder(mVoiceCallback, vModel)
        vModel.isVoiceRecording.postValue(true)
        mVoiceRecorder?.start()
    }
    private fun stopVoiceRecorder() {
        mVoiceRecorder?.stop()
        mVoiceRecorder = null
        vModel.isVoiceRecording.postValue(false)
    }
}