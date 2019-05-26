package com.example.gRPCTest

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.google.auth.Credentials
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import io.grpc.*
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.okhttp.OkHttpChannelProvider
import io.grpc.stub.StreamObserver
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

class SpeechService : Service() {
    private val TAG = "SpeechService"

    private val HOSTNAME = "speech.googleapis.com"
    private val PORT = 443

    private val PREFS = "SpeechService"
    private val PREF_ACCESS_TOKEN_VALUE = "access_token_value"
    private val PREF_ACCESS_TOKEN_EXPIRATION_TIME = "access_token_expiration_time"
    private val SCOPE = Collections.singletonList("https://www.googleapis.com/auth/cloud-platform")

    /** We reuse an access token if its expiration time is longer than this.  */
    private val ACCESS_TOKEN_EXPIRATION_TOLERANCE = 30 * 60 * 1000 // thirty minutes

    /** We refresh the current access token before it expires.  */
    private val ACCESS_TOKEN_FETCH_MARGIN = 60 * 1000 // one minute


    private val mBinder: SpeechBinder = SpeechBinder()
    private var mHandler: Handler? = null
    private var mAccessTokenTask: AccessTokenTask? = null
    private val mListeners = mutableListOf<Listener>()

    //
    private lateinit var mApi: SpeechGrpc.SpeechStub
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
                var text: String = ""
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
                Log.i(TAG, "API completed.")
            }

            override fun onError(t: Throwable?) {
                Log.e(TAG, "Error calling the API.", t)
            }
        }
        mFileResponseObserver = object : StreamObserver<RecognizeResponse> {
            override fun onNext(response: RecognizeResponse?) {
                val isFinal = false
                var text = ""
                response?.let {
                    if (response.resultsCount > 0) {
                        val result = response.getResults(0)
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
                Log.i(TAG, "API completed.")
            }

            override fun onError(t: Throwable?) {
                Log.e(TAG, "Error calling the API.", t)
            }
        }


    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }
    override fun onDestroy() {
        super.onDestroy()
        mHandler?.let { it.removeCallbacks(mFetchAccessTokenRunnable) }
        mHandler = null
        // Release gRPC channel
        val channel: ManagedChannel = mApi.channel as ManagedChannel
        if (channel.isShutdown) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error shutting down the gRPC channel. $e")
            }
        }
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }
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

    fun getDefaultLanguageCode(): String {
        val local = Locale.getDefault()
        val language = StringBuilder(local.language)
        val country = local.country
        if (country.isNotEmpty()) {
            language.append("-")
            language.append(country)
        }
        return language.toString()
    }

    val mFetchAccessTokenRunnable = object : Runnable {
        override fun run() {
            fetchAccessToken()
        }
    }

    fun startRecognizing(sampleRate: Int) {
        if (mApi == null) {
            Log.w(TAG, "API not ready. Ignoring the request")
            return
        } else {
            mRequestObserver = mApi.streamingRecognize(mResponseObserver)
            val recognitionConfig = RecognitionConfig.newBuilder()
                .setLanguageCode(getDefaultLanguageCode())
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
        }
    }

    fun recognize(data: ByteArray, size: Int) {
        val streamingRecognizeRequest = StreamingRecognizeRequest.newBuilder()
            .setAudioContent(ByteString.copyFrom(data, 0, size))
            .build()
        mRequestObserver?.let {
            it.onNext(streamingRecognizeRequest)
        }
    }

    fun finishRecognizing() {
        mRequestObserver?.let {
            it.onCompleted()
            mRequestObserver = null
        }
    }

    private inner class AccessTokenTask : AsyncTask<Void, Void, AccessToken>() {

        override fun doInBackground(vararg params: Void?): AccessToken? {

            val prefs: SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val tokenValue = prefs.getString(PREF_ACCESS_TOKEN_VALUE, null)
            val expirationTime: Long = prefs.getLong(PREF_ACCESS_TOKEN_EXPIRATION_TIME, -1L)

            // check if the current token is still valid
            if (tokenValue != null && expirationTime > 0) {
                if (expirationTime > System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_TOLERANCE) {
                    return AccessToken(tokenValue, Date(expirationTime))
                }
            }
            val inputStream = resources.openRawResource(R.raw.credential)
            try {
                val credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(SCOPE)
                val token = credentials.refreshAccessToken()
                prefs.edit()
                    .putString(PREF_ACCESS_TOKEN_VALUE, token.tokenValue)
                    .putLong(
                        PREF_ACCESS_TOKEN_EXPIRATION_TIME,
                        token.expirationTime.time
                    )
                    .apply()
                return token
            } catch (e: IOException) {
                Log.e(TAG, "Fail to obtain access token $e")
            }
            return null
        }

        override fun onPostExecute(result: AccessToken) { // Tokenの取得が済んだのち
            super.onPostExecute(result)
            mAccessTokenTask = null
            val googleCredentials = GoogleCredentials(result).createScoped(SCOPE)
            val interceptor = GoogleCredentialsInterceptor(googleCredentials)
            val channel = OkHttpChannelProvider()
                .builderForAddress(HOSTNAME, PORT)
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
        // @param text The text.
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