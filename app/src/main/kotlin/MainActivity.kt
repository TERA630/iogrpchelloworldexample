package com.example.gRPCTest


import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.Manifest.permission
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val STATE_RESULTS = "results"
const val COLOR_HEARING = "colorHearing"
const val COLOR_NOT_HEARING = "colorNotHearing"

class MainActivity : AppCompatActivity() {

    private val mrequestCodeRecord = 1
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
        vModel = ViewModelProviders.of(this@MainActivity).get(MainViewModel::class.java)
        vModel.isVoiceRecording.postValue(false)

        mColorHearing = getColor(R.color.status_hearing)
        mColorNotHearing = getColor(R.color.status_not_hearing)

        val resultOptional = savedInstanceState?.getStringArrayList(STATE_RESULTS)
        val result = if (resultOptional.isNullOrEmpty()) arrayListOf("one", "two", "three", "four", "five")
        else resultOptional

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
                ActivityCompat.requestPermissions(this, arrayOf(permission.RECORD_AUDIO), mrequestCodeRecord)
            }
        }
        vModel.isVoiceRecording.observe(this, Observer<Boolean> { t ->
            if (t != null && t == true) conditionLabel.setTextColor(mColorHearing)
            else conditionLabel.setTextColor(mColorNotHearing)
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
                if (isFinal) mVoiceRecorder?.dismiss()
                if (text.isNotEmpty()) {
                    val job = CoroutineScope(Dispatchers.Default).launch {
                        vModel.recognizedChannel.send(text)
                    }
                    runOnUiThread {
                        if (isFinal) {
                            vModel.isVoiceRecording.value = false
                            mAdapter.addResult(text)
                            recyclerView.smoothScrollToPosition(0)
                        } else conditionLabel.text = text
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
                vModel.isVoiceRecording.postValue(false)
            }
        }
        startRecordingBtn.setOnClickListener {
            val uIScope = CoroutineScope(Dispatchers.Main)
            uIScope.launch {
                val channelText = vModel.recognizedChannel.receive()
                channelViewer.text = channelText
            }
        }
    }

    private fun startVoiceRecorder() {
        mVoiceRecorder?.stop()
        mVoiceRecorder = VoiceRecorder(mVoiceCallback)
        mVoiceRecorder?.start()
    }

    private fun stopVoiceRecorder() {
        mVoiceRecorder?.stop()
        mVoiceRecorder = null
    }
}