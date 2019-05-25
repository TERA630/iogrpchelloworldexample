package com.example.gRPCTest

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import android.os.IBinder
import android.os.PersistableBundle
import android.Manifest.permission
import android.content.*
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_result.*

const val STATE_RESULTS = "results"
const val COLOR_HEARING = "colorHearing"
const val COLOR_NOT_HEARING = "colorNotHearing"

class MainActivity : AppCompatActivity() {
    
    private val REQUEST_CODE_RECORD = 1
    private var mColorHearing = 0
    private var mColorNotHearing = 0
    private var mSpeechService: SpeechService? = null // given after SpeechService begun
    private var mVoiceRecorder: VoiceRecorder? = null // given after on Start and permission was granted


    private lateinit var mAdapter: ResultAdapter // initialized by on Create
    private lateinit var mSpeechServiceListener: SpeechService.Listener // initialized by on Create
    private lateinit var mServiceConnection: ServiceConnection // initialized by onCreate
    private lateinit var mVoiceCallback: VoiceRecorder.Callback // initialized by onCreate


    // Activity main life Cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val linearLayoutManager = LinearLayoutManager(this.baseContext)
        mColorHearing = getColor(R.color.status_hearing)
        mColorNotHearing = getColor(R.color.status_not_hearing)
        recyclerView.layoutManager = linearLayoutManager


        val result = if (savedInstanceState != null) {
            savedInstanceState.getStringArrayList(STATE_RESULTS) ?: arrayListOf("fail to get savedInstance..")
        } else {
            arrayListOf("one", "two", "three", "four","five")
        }

        mSpeechServiceListener = object:SpeechService.Listener {
            override fun onSpeechRecognized(text: String, isFinal: Boolean) {
                if(isFinal) mVoiceRecorder?.dismiss()
                if( text.isNotEmpty()) {
                    runOnUiThread {
                        if(isFinal) {
                            conditionLabel.text = ""
                            mAdapter.addResult(text)
                            recyclerView.smoothScrollToPosition(0)
                        }
                        else conditionLabel.text = text
                    }
                }
            }
        }

        mServiceConnection = object: ServiceConnection{
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                mSpeechService = SpeechService().from(service)
                mSpeechService?.addListener(listener = mSpeechServiceListener)
                Log.i("test","service connected.")
                status.visibility = View.VISIBLE
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mSpeechService = null
                Log.i("test","service disconnected")
            }
        }
        mVoiceCallback = object : VoiceRecorder.Callback { // 音声認識エンジン
            override fun onVoiceStart() {
                showStatus(true)
                Log.i("test", "voice coming")
            }

            override fun onVoice(data: ByteArray, size: Int) {
                super.onVoice(data, size)
                Log.i("test", "voice continues.")
            }

            override fun onVoiceEnd() {
                super.onVoiceEnd()
                Log.i("test", "voice ended")
            }
        }
        startRecordingBtn.setOnClickListener {
            Log.i("test", "button was pushed.")
        }

        mAdapter = ResultAdapter(result)
        recyclerView.adapter = mAdapter
    }
    override fun onStart() {
        super.onStart()
        // Prepare Cloud Speech API
        val intent = Intent(this, SpeechService::class.java)
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)

        val audioPermission = checkSelfPermission(this.baseContext, permission.RECORD_AUDIO)
        if (audioPermission == PERMISSION_GRANTED) {
            Log.i("test", "this app has already permission.")
            startVoiceRecorder()
        } else if (shouldShowRequestPermissionRationale(permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setTitle("permission")
                .setMessage("このアプリの利用には音声の録音を許可してください.")
            Log.w("test", "permission request was disabled")
        } else {
            Log.w("test", "this app has no permission yet.")
            ActivityCompat.requestPermissions(this, arrayOf(permission.RECORD_AUDIO), REQUEST_CODE_RECORD)
        }

    }
    override fun onStop() {
        stopVoiceRecorder()
        mSpeechService?.let { it.removeListener(mSpeechServiceListener) }
        unbindService(mServiceConnection)
        mSpeechService = null
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) { // on Pauseや回転後 on Stop前
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
        if(!stringArray.isNullOrEmpty()) mAdapter.upDateResultList(stringArray)
    }

    // Activity Event
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
            // AUDIO RECORD Permission Granted
            Log.i("test", "permission was granted by request")
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
    private fun startVoiceRecorder() {
        mVoiceRecorder?.let { it.stop() }
        mVoiceRecorder = VoiceRecorder(mVoiceCallback)
        mVoiceRecorder?.start()
    }

    private fun stopVoiceRecorder() {
        mVoiceRecorder?.let {
            it.stop()
        }
        mVoiceRecorder = null
    }

    private fun showStatus(hearingVoice: Boolean) {
        runOnUiThread {
            val color = if (hearingVoice) mColorHearing else mColorNotHearing
            status.setTextColor(color)
        }
    }


}