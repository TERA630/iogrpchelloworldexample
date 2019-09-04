package com.example.gRPCTest

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel


fun <T> MutableLiveData<T>.default(initialValue: T) = apply { postValue(initialValue) }

class MainViewModel : ViewModel() {
    var isAudioRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isVoiceRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isRecognizing: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)

    val recognizedChannel: Channel<String> = Channel()

}


