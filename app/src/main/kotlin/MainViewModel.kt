package com.example.gRPCTest

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel


fun <T> MutableLiveData<T>.default(initialValue: T) = apply { postValue(initialValue) }

class MainViewModel : ViewModel() {
    var isAudioRecordworking: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isVoiceRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isRecognizing: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)

    val recognizedChannel: Channel<String> = Channel()
}


