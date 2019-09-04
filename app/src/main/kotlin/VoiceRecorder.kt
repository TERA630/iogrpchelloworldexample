package com.example.gRPCTest

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.abs


const val AMPLITUDE_THRESHOLD = 1280 // original 1500
const val SPEECH_TIMEOUT_MILLIS = 2000
const val MAX_SPEECH_LENGTH_MILLIS = 30 * 1000

class VoiceRecorder(private val mCallback: Callback, private val vModel: MainViewModel) {
    private val cSampleRateCandidates = intArrayOf(16000, 11025, 22050, 44100)
    private val cChannel = AudioFormat.CHANNEL_IN_MONO
    private val cEncoding = AudioFormat.ENCODING_PCM_16BIT

    interface Callback { // 実装を今回はMainActivityに委譲
        fun onVoiceStart()  // called when the recorder starts hearing voice.
        fun onVoice(data: ByteArray, size: Int) {}// called when the recorder is hearing voice.
        fun onVoiceEnd() {} // called when the recorder stops hearing voice.
    }
    private lateinit var mAudioRecord: AudioRecord
    private lateinit var mBuffer: ByteArray

    private var mLastVoiceHeardMillis = java.lang.Long.MAX_VALUE
    private var mVoiceStartedMillis: Long = 0

    private val supervisor = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Default + supervisor)

    fun start() { // Called by MainActivity@StartvoiceRecorder
        mAudioRecord = createAudioRecord()
        mAudioRecord.startRecording()

        scope.launch {
            // conbinedContext element= DefaultDispatcher left supervisorJobImpl
            processVoice(scope)
        }

    }
    fun stop() {
        supervisor.cancel()
        dismiss()
        mAudioRecord.stop()
        mAudioRecord.release()
    }

    fun dismiss() { // Lock2回はずしあt
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE
            mCallback.onVoiceEnd()
        }
    }

    fun getSampleRate() = mAudioRecord.sampleRate

    private fun createAudioRecord(): AudioRecord {
        for (sampleRate in cSampleRateCandidates) {
            val sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, cChannel, cEncoding)
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue
            }
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, cChannel, cEncoding, sizeInBytes)
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                mBuffer = ByteArray(sizeInBytes)
                vModel.isAudioRecording.postValue(true)
                return audioRecord
            } else {
                audioRecord.release()
                vModel.isAudioRecording.postValue(false)
            }
        }
        throw java.lang.RuntimeException("Cannot instantiate VoiceRecorder")
    }

    // Continuously processes the captured audio and Notifies
    private fun processVoice(scope: CoroutineScope) {
            while (scope.isActive) {
                val size = mAudioRecord.read(mBuffer, 0, mBuffer.size)
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

    private fun end() {
        mLastVoiceHeardMillis = Long.MAX_VALUE
        mCallback.onVoiceEnd()
    }

    private fun isHearingVoice(buffer: ByteArray, size: Int): Boolean {
        for (i in 0 until size - 1 step 2) { // Android writing out big endian
            var s = buffer[i + 1].toInt() // Little endian  上位バイト
            if (s < 0) s *= -1 // 負数なら正数に
            s = s shl 8 // 上位バイト　
            s += abs(buffer[i].toInt()) //　下位バイト
                if (s > AMPLITUDE_THRESHOLD) return true
        }
        return false
    }
}