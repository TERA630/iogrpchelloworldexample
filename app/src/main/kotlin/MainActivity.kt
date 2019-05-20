package io.grpc.hallowrldexample

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PersistableBundle
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import io.grpc.helloworldexample.R
import kotlinx.android.synthetic.main.activity_main.*

const val STATE_RESULTS = "results"

class MainActivity : AppCompatActivity() {

    private lateinit var mAdapter: ResultAdapter
    //  private var mVoiceRecorder:VoiceRecorder? = null
    //  private lateinit var mSpeechService: SpeechService

    /*  private var mServiceConnection:ServiceConnection = object: ServiceConnection{
          override fun onServiceConnected(name: ComponentName?, service: IBinder) {
              mSpeechService = SpeechService().from(service)
              Log.i("test","service connected.")

          }
          override fun onServiceDisconnected(name: ComponentName?) {
              TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
          }
      }*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val linearLayoutManager = LinearLayoutManager(this.baseContext)
        recyclerView.layoutManager = linearLayoutManager


        val result = if (savedInstanceState != null) {
            savedInstanceState.getStringArrayList(STATE_RESULTS) ?: arrayListOf("fail to get savedInstance..")
        } else {
            arrayListOf("one", "two", "three", "four")
        }
        mAdapter = ResultAdapter(result)
        recyclerView.adapter = mAdapter

    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState?.putStringArrayList(STATE_RESULTS, mAdapter.getResults())
    }

    /* override fun onStart() {
         super.onStart()
         // Prepare Cloud Speech API
         val intent = Intent(this,SpeechService::class.java)
         bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
     }*/

}