package com.example.gRPCTest

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.google.auth.Credentials
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import io.grpc.*
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.okhttp.OkHttpChannelProvider
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max


/** We reuse an access token if its expiration time is longer than this.  */
const val ACCESS_TOKEN_EXPIRATION_TOLERANCE = 30 * 60 * 1000 // thirty minutes
/** We refresh the current access token before it expires.  */
const val ACCESS_TOKEN_FETCH_MARGIN = 60 * 1000 // one minute
const val PREF_ACCESS_TOKEN_EXPIRATION_TIME = "access_token_expiration_time"

class SpeechService : LifecycleService() {
    private val mTag = "SpeechService"

    private val googleHostName = "speech.googleapis.com"
    private val PortOfGoogleAPI = 443

    private val preferenceOfThis = "SpeechService"
    private val prefAccessTokenValue = "access_token_value"
    private val scopeOfGoogleAPI =
        Collections.singletonList("https://www.googleapis.com/auth/cloud-platform")

    private val mBinder: SpeechBinder = SpeechBinder()
    private var mHandler: Handler? = null
    private var mAccessTokenTask: AccessTokenTask? = null
    private val tokenJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + tokenJob)
    private val mListeners = mutableListOf<Listener>()

    //
    private var mApi: SpeechGrpc.SpeechStub? = null
    private lateinit var mFileResponseObserver: StreamObserver<RecognizeResponse>
    private lateinit var mResponseObserver: StreamObserver<StreamingRecognizeResponse>
    private var mRequestObserver: StreamObserver<StreamingRecognizeRequest>? = null

    // Service lifecycle
    override fun onCreate() {
        super.onCreate()




        mHandler = Handler()
        fetchAccessToken()
        mResponseObserver = object : StreamObserver<StreamingRecognizeResponse> {
            override fun onNext(response: StreamingRecognizeResponse?) {
                var text = ""
                var isFinal = false
                response?.let {
                    if (it.resultsCount > 0) {
                        val result = it.getResults(0)
                        isFinal = result.isFinal
                        if (result.alternativesCount > 0) {
                            val alternative = result.getAlternatives(0)
                            text = alternative.transcript
                        }
                    }
                    if (text.isNotEmpty()) {
                        for (listener in mListeners) {
                            listener.onSpeechRecognized(text, isFinal)
                        }

                    }
                }
            }
            override fun onCompleted() {
                Log.i(mTag, "API completed.")
            }
            override fun onError(t: Throwable?) {
                Log.e(mTag, "Error calling the API.", t)
            }
        }
        mFileResponseObserver = object : StreamObserver<RecognizeResponse> {
            override fun onNext(response: RecognizeResponse?) {
                val isFinal = false
                var text = ""
                response?.let {
                    if (it.resultsCount > 0) {
                        val result = it.getResults(0)
                        if (result.alternativesCount > 0) {
                            val alternative = result.getAlternatives(0)
                            text = alternative.transcript
                        }

                    }
                }
                if (text.isNotEmpty()) {
                    for (listener in mListeners) {
                        listener.onSpeechRecognized(text, isFinal)
                    }
                }
            }
            override fun onCompleted() {
                Log.i(mTag, "API completed.")
            }

            override fun onError(t: Throwable?) {
                Log.e(mTag, "Error calling the API.", t)
            }
        }
    }
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return mBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        tokenJob.cancel()
        mHandler?.removeCallbacks(mFetchAccessTokenRunnable)
        mHandler = null
        // Release gRPC channel
        mApi?.let {
            val channel = it.channel as ManagedChannel
            if (channel.isShutdown) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.e(mTag, "Error shutting down the gRPC channel. $e")
                }
            }
        }
    }

    fun addListener(listener: Listener) = mListeners.add(listener)
    fun removeListener(listener: Listener) = mListeners.remove(listener)
    fun from(binder: IBinder): SpeechService {
        return (binder as SpeechBinder).getService()
    }

    private fun fetchAccessToken() {

        if (mAccessTokenTask != null) {
            return
        } else {
            mAccessTokenTask = AccessTokenTask() // TokenTaskが開始されていなければ､非同期に暗号鍵をRAW/JSONから作成し､ACCESS TOKENを新しくする｡
            mAccessTokenTask!!.execute() //　
        }
    }

    private fun getDefaultLanguageCode(): String {
        val local = Locale.getDefault()
        val language = StringBuilder(local.language)
        val country = local.country
        if (country.isNotEmpty()) {
            language.append("-")
            language.append(country)
        }
        return language.toString()
    }

    val mFetchAccessTokenRunnable = Runnable { fetchAccessToken() }

    fun startRecognizing(sampleRate: Int) {
        mApi?.let {
            mRequestObserver = it.streamingRecognize(mResponseObserver)
            val recognitionConfig = RecognitionConfig.newBuilder()
                .setLanguageCode("ja-JP")
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(sampleRate)
                .build()
            val streamingRecognitionConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recognitionConfig)
                .setInterimResults(true)
                .setSingleUtterance(true)
                .build()
            val streamingRecognizeRequest = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingRecognitionConfig)
                .build()
            mRequestObserver?.onNext(streamingRecognizeRequest)
        } ?: run {
            Log.w(mTag, "API not ready. Ignoring the request")
            return
        }
    }

    fun recognize(data: ByteArray, size: Int) {
        val streamingRecognizeRequest = StreamingRecognizeRequest.newBuilder()
            .setAudioContent(ByteString.copyFrom(data, 0, size))
            .build()
        mRequestObserver?.onNext(streamingRecognizeRequest)
    }


    fun finishRecognizing() {
        mRequestObserver?.let {
            it.onCompleted()
            mRequestObserver = null
        }
    }

    private fun getAccessTokenFromPreference(): AccessToken? {

        val prefs: SharedPreferences = getSharedPreferences(preferenceOfThis, Context.MODE_PRIVATE)
        val tokenValue = prefs.getString(prefAccessTokenValue, null)
        val expirationTime: Long = prefs.getLong(PREF_ACCESS_TOKEN_EXPIRATION_TIME, -1L)

        return if (tokenValue.isNullOrEmpty() || expirationTime < 0) null
        else if (expirationTime > System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_TOLERANCE)
            AccessToken(tokenValue, Date(expirationTime))
        else null
    }

    private fun saveTokenToPref(token: AccessToken) {
        val prefs: SharedPreferences = getSharedPreferences(preferenceOfThis, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(prefAccessTokenValue, token.tokenValue)
            .putLong(PREF_ACCESS_TOKEN_EXPIRATION_TIME, token.expirationTime.time)
            .apply()
    }

    private inner class AccessTokenTask : AsyncTask<Void, Void, AccessToken>() {

        override fun doInBackground(vararg params: Void?): AccessToken? {
            val tokenFromPref = getAccessTokenFromPreference()
            if (tokenFromPref != null) return tokenFromPref

            val inputStream = resources.openRawResource(R.raw.credential)
            try {
                val credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(scopeOfGoogleAPI)
                val token = credentials.refreshAccessToken()
                saveTokenToPref(token)
                return token
            } catch (e: IOException) {
                Log.e(mTag, "Fail to obtain access token $e")
            }
            return null
        }
        override fun onPostExecute(result: AccessToken) { // Tokenの取得が済んだのち
            super.onPostExecute(result)
            mAccessTokenTask = null
            val googleCredentials = GoogleCredentials(result).createScoped(scopeOfGoogleAPI)
            val interceptor = GoogleCredentialsInterceptor(googleCredentials)
            val channel = OkHttpChannelProvider()
                .builderForAddress(googleHostName, PortOfGoogleAPI)
                .nameResolverFactory(DnsNameResolverProvider())
                .intercept(interceptor)
                .build()
            mApi = SpeechGrpc.newStub(channel)
            mHandler?.postDelayed(
                mFetchAccessTokenRunnable,
                max(
                    result.expirationTime.time - System.currentTimeMillis() - ACCESS_TOKEN_FETCH_MARGIN,
                    ACCESS_TOKEN_EXPIRATION_TOLERANCE.toLong()
                )
            )
        }
    }

    interface Listener {
        // called when a new piece of text was recognized by the CloudSpeechAPI
        // @param isFinal when the API finished processing audio.
        fun onSpeechRecognized(text: String, isFinal: Boolean)
    }

    inner class SpeechBinder : Binder() {
        fun getService(): SpeechService {
            return this@SpeechService
        }
    }

    private class GoogleCredentialsInterceptor(
        private val mCredentials: Credentials
    ) : ClientInterceptor {

        private var mLastMetadata: Map<String, List<String>>? = null
        private lateinit var mCached: Metadata

        override fun <ReqT, RespT> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel
        ): ClientCall<ReqT, RespT> {
            val newClientCallDelegate = next.newCall(method, callOptions)
            val clientCall =
                object : ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(newClientCallDelegate) {

                    override fun checkedStart(responseListener: Listener<RespT>?, headers: Metadata) {
                        val cachedSaved: Metadata
                        val uri = serviceUri(next, method)
                        synchronized(this) {
                            val latestMetadata = getRequestMetadata(uri)
                            if (mLastMetadata == null || mLastMetadata != latestMetadata) {
                                mLastMetadata = latestMetadata
                                mCached = toHeaders(mLastMetadata!!)

                            }
                            cachedSaved = mCached
                        }
                        headers.merge(cachedSaved)
                        delegate().start(responseListener, headers)
                    }
                }
            return clientCall
        }

        @Throws(StatusException::class)
        private fun <ReqT, RespT> serviceUri(channel: Channel, method: MethodDescriptor<ReqT, RespT>): URI {
            val authority = channel.authority()
            if (authority == null) {
                throw Status.UNAUTHENTICATED
                    .withDescription("Channel has no authority")
                    .asException()
            } else {
                val scheme = "https"
                val defaultPort = 443
                val path = "/${MethodDescriptor.extractFullServiceName(method.fullMethodName)}"
                try {
                    val uri = URI(scheme, authority, path, null, null)
                    if (uri.port == defaultPort) {
                        removePort(uri)
                    }
                    return uri
                } catch (e: URISyntaxException) {
                    throw Status.UNAUTHENTICATED
                        .withDescription("Unable to construct service URI for auth")
                        .withCause(e).asException()
                }
                // The default port must not be present,Alternative ports should be present.
            }
        }

        @Throws(StatusException::class)
        fun removePort(uri: URI): URI {
            try {
                return URI(
                    uri.scheme, uri.userInfo, uri.host, -1, uri.path, uri.query, uri.fragment
                )
            } catch (e: URISyntaxException) {
                throw Status.UNAUTHENTICATED
                    .withDescription("Unable to construct service URI after removing port")
                    .withCause(e).asException()
            }
        }

        @Throws(StatusException::class)
        fun getRequestMetadata(uri: URI): Map<String, List<String>> {
            try {
                return mCredentials.getRequestMetadata(uri)
            } catch (e: IOException) {
                throw Status.UNAUTHENTICATED.withCause(e).asException()
            }
        }

        fun toHeaders(metadata: Map<String, List<String>>): Metadata {
            val headers: Metadata = Metadata()
            for (key in metadata.keys) {
                val headerKey = Metadata.Key.of(
                    key, Metadata.ASCII_STRING_MARSHALLER
                )
                val list = metadata[key] ?: emptyList()
                for (value in list) {
                    headers.put(headerKey, value)
                }
            }
            return headers
        }
    }
}

