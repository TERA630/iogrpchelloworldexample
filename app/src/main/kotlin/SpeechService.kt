package com.example.gRPCTest

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class SpeechService : Service() {
    private val TAG = "SpeechService"
    private lateinit var mBinder: Binder

    override fun onBind(intent: Intent): IBinder { // must be called only after on create
        Log.i(TAG,"$TAG was binded")
        return mBinder
    }
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG,"$TAG was created")
        mBinder = SpeechBinder()
       // mHandler = Handler()
       // fetchAccessToken()
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"$TAG was destroyed")
    }

    fun from(binder:IBinder):SpeechService{
        return (binder as SpeechBinder).getService()
    }
    private inner class SpeechBinder:Binder() {
     fun getService():SpeechService{
         return this@SpeechService
     }
    }
}
