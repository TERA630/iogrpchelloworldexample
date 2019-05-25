package com.example.gRPCTest

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.provider.MediaStore
import android.util.Log
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class VoiceRecorder(private val mCallback: Callback) {
    private val SAMPLE_RATE_CANDIDATES = intArrayOf(16000, 11025, 22050, 44100)
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private val AMPLITUDE_THRESHOLD = 1500
    private val SPEECH_TIMEOUT_MILLIS = 2000
    private val MAX_SPEECH_LENGTH_MILLIS = 30 * 1000

    interface Callback {
        fun onVoiceStart()  // called when the recorder starts hearing voice.
        fun onVoice(data: ByteArray, size: Int) { // called when the recorder is hearing voice.
            // @param data: The audio data in AudioFormat#ENCORDING_PCM_16BIT
            // @param size: The size of actual data in
        }

        fun onVoiceEnd() {} // called when the recorder stops hearing voice.

    }

    private var mAudioRecord: AudioRecord? = null
    private var mThread: Thread? = null
    private lateinit var mBuffer: ByteArray
    private var mLock = java.util.concurrent.locks.ReentrantLock()


    /** The timestamp of the last time that voice is heard.  */
    private var mLastVoiceHeardMillis = java.lang.Long.MAX_VALUE
    /** The timestamp when the current voice is started.  */
    private var mVoiceStartedMillis: Long = 0

    fun start() {
        stop() // if it is current ongoing, stop it.
        mAudioRecord = createAudioRecord()
        if (mAudioRecord == null) throw java.lang.RuntimeException("Cannot instantiate VoiceRecorder")
        else {
            Log.i("test", "voice recorder started..")
            mAudioRecord?.startRecording()
            mThread = Thread(ProcessVoice())
            mThread!!.start()
        }
    }

    fun stop() {
        mLock.withLock {
            dismiss()
            mThread?.let {
                it.interrupt()
                mThread = null
            }
            mAudioRecord?.let {
                it.stop()
                it.release()
                mAudioRecord = null
            }
        }
    }

    fun dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE
            mCallback.onVoiceEnd()
        }
    }
    // Retries the sample rate currently used to record audio

    fun getSampleRate() {
        val result = mAudioRecord?.let {
            it.sampleRate
        } ?: 0
    }

    private fun createAudioRecord(): AudioRecord? {

        for (sampleRate in SAMPLE_RATE_CANDIDATES) {
            val sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue
            }
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, CHANNEL, ENCODING, sizeInBytes
            )
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

    inner class ProcessVoice : Runnable {
        override fun run() {
            runLoop@ while (true) {
                if (Thread.currentThread().isInterrupted) {
                    Log.w("test", "thread interrupted")
                    break@runLoop
                }
                mLock.withLock {
                    mAudioRecord?.let {
                            val size = it.read(mBuffer, 0, mBuffer.size)
                            val now = System.currentTimeMillis()
                            if (isHearingVoice(mBuffer, size)) {
                                if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                                    mVoiceStartedMillis = now
                                    mCallback.onVoiceStart()
                                }
                                mCallback.onVoice(mBuffer, size)
                                if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                                    end()
                                } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                                    mCallback.onVoice(mBuffer, size)
                                }
                            if (now - mVoiceStartedMillis > SPEECH_TIMEOUT_MILLIS) {
                                end()
                            }
                            }
                        }
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
            if (s > AMPLITUDE_THRESHOLD) {
                return true
            }
        }
        return false
    }
}