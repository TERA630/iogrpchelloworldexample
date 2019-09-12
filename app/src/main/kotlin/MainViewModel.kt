package com.example.gRPCTest

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach

fun <T> MutableLiveData<T>.default(initialValue: T) = apply { postValue(initialValue) }

class MainViewModel : ViewModel(),CoroutineScope {
    var isAudioRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isVoiceRecording: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)
    var isRecognizing: MutableLiveData<Boolean> = MutableLiveData<Boolean>().default(false)

    val recognizedChannel: Channel<String> = Channel()

    private val job= Job()
    override val coroutineContext = Dispatchers.Main + job

    @ObsoleteCoroutinesApi
    fun init(){
        val actor = actor<ActionMsg>(coroutineContext,0,CoroutineStart.LAZY,null,{
            consumeEach { actionMsg ->
                when(actionMsg){
                    is Activate ->
                    is Done ->

                }

            }

        })


    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

}


sealed class ActionMsg
class Activate(id:Int): ActionMsg()
class Done(ack: CompletableDeferred<Boolean>):ActionMsg()
