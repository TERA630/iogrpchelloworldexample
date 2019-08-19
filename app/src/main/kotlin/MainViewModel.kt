package com.example.gRPCTest

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel

class MainViewModel : ViewModel(){

    var isVoiceRecording: MutableLiveData<Boolean> = MutableLiveData()
    val recognizedChannel = Channel<String>()


}