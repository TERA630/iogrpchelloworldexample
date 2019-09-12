package com.example.gRPCTest

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel

fun <T> MutableLiveData<T>.default(initialValue: T) = apply { postValue(initialValue) }

class MainViewModel : ViewModel(),CoroutineScope {
    var isAudioRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isVoiceRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isRecognizing: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)

    val recognizedChannel: Channel<String> = Channel()
    private val job= Job()
    override val coroutineContext = Dispatchers.Main + job
    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

}

