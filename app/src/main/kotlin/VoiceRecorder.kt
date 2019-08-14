package com.example.gRPCTest

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

const val AMPLITUDE_THRESHOLD = 1500
const val SPEECH_TIMEOUT_MILLIS = 2000
const val MAX_SPEECH_LENGTH_MILLIS = 30 * 1000

class VoiceRecorder(private val mCallback: Callback) {
    private val cSampleRateCandidates = intArrayOf(16000, 11025, 22050, 44100)
    private val cChannel = AudioFormat.CHANNEL_IN_MONO
    private val cEncoding = AudioFormat.ENCODING_PCM_16BIT
    private var processVoiceJob: Job? = null

    interface Callback {
        fun onVoiceStart()  // called when the recorder starts hearing voice.
        fun onVoice(data: ByteArray, size: Int) {}// called when the recorder is hearing voice.
        fun onVoiceEnd() {} // called when the recorder stops hearing voice.
    }

    private var mAudioRecord: AudioRecord? = null
    private lateinit var mBuffer: ByteArray
    private var mLock = java.util.concurrent.locks.ReentrantLock()


    /** The timestamp of the last time that voice is heard.  */
    private var mLastVoiceHeardMillis = java.lang.Long.MAX_VALUE
    /** The timestamp when the current voice is started.  */
    private var mVoiceStartedMillis: Long = 0

    fun start() {
        if (processVoiceJob != null) stop() // if it is current ongoing, stop it.

        mAudioRecord = createAudioRecord() ?: throw java.lang.RuntimeException("Cannot instantiate VoiceRecorder")
        mAudioRecord?.startRecording()
        val scope = CoroutineScope(Dispatchers.Default)
        processVoiceJob = scope.launch {
            while (isActive) {
                processVoice(scope)
            }
        }
        Log.i("voiceRecorder", "Coroutine start?")
    }

    fun stop() {
        mLock.withLock {
            dismiss()
            processVoiceJob?.cancel()

            mAudioRecord?.let {
                it.stop()
                it.release()
            }
            mAudioRecord = null
        }
    }
    fun dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE
            mCallback.onVoiceEnd()
        }
    }
    // Retries the sample rate currently used to record audio

    fun getSampleRate() = mAudioRecord?.sampleRate ?: 0

    private fun createAudioRecord(): AudioRecord? {

        for (sampleRate in cSampleRateCandidates) {
            val sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, cChannel, cEncoding)
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue
            }
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, cChannel, cEncoding, sizeInBytes)
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                mBuffer = ByteArray(sizeInBytes)
                return audioRecord
            } else {
                audioRecord.release()
            }
        }
        return null
    }
    // Continuously processes the captured audio and Notifies

    private fun processVoice(scope: CoroutineScope) {
        mLock.withLock {
            mAudioRecord?.let {
                val size = it.read(mBuffer, 0, mBuffer.size)
                val now = System.currentTimeMillis()
                if (isHearingVoice(mBuffer, size)) {
                    if (mLastVoiceHeardMillis == Long.MAX_VALUE) { // ボイスレコーダー開始時にmLast..はMAX_VALUEに､mVoiceStart..は今に
                        mVoiceStartedMillis = now
                        mCallback.onVoiceStart()
                    }
                    mCallback.onVoice(mBuffer, size)
                    mLastVoiceHeardMillis = now
                    if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) end() // 経過30秒で終わり
                } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                    mCallback.onVoice(mBuffer, size)
                    if (now - mVoiceStartedMillis > SPEECH_TIMEOUT_MILLIS) end() // 無音は2秒でタイムアウト
                }
                }
            }
    }

    private fun end() {
        mLastVoiceHeardMillis = Long.MAX_VALUE
        mCallback.onVoiceEnd()
    }

        private fun isHearingVoice(buffer: ByteArray, size: Int): Boolean {
            for (i in 0 until size - 1 step 2) {
                var s = buffer[i + 1].toInt() // Little endian  上位バイト
                if (s < 0) s *= -1 // 負数なら正数に
                s = s shl 8 // 上位バイト　
                s += Math.abs(buffer[i].toInt()) //　下位バイト
                if (s > AMPLITUDE_THRESHOLD) return true
            }
            return false
    }
}