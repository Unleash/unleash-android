package io.getunleash.android.polling

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.android.data.FetchResponse
import io.getunleash.android.data.Parser
import io.getunleash.android.data.ProxyResponse
import io.getunleash.android.data.Status
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.ToggleResponse
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.errors.NoBodyException
import io.getunleash.android.errors.NotAuthorizedException
import io.getunleash.android.unleashScope
import io.getunleash.errors.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Http Client for fetching data from Unleash Proxy.
 * By default creates an OkHttpClient with readTimeout set to 2 seconds and a cache of 10 MBs
 * @param httpClient - the http client to use for fetching toggles from Unleash proxy
 */
open class UnleashFetcher(
    private val unleashContext: StateFlow<UnleashContext>,
    private val appName: String,
    private val proxyUrl: HttpUrl,
    private val httpClient: OkHttpClient,
    private val applicationHeaders: Map<String, String> = emptyMap()
) : Closeable {
    companion object {
        private const val TAG = "UnleashFetcher"
    }
    private var etag: String? = null
    private val featuresReceivedFlow = MutableSharedFlow<UnleashState>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val coroutineContextForContextChange: CoroutineContext = Dispatchers.IO

    fun getFeaturesReceivedFlow(): SharedFlow<UnleashState> = featuresReceivedFlow.asSharedFlow()

    init {
        // listen to unleash context state changes
        unleashScope.launch {
            unleashContext.collect {
                withContext(coroutineContextForContextChange) {
                    Log.d(TAG, "Unleash context changed: $it")
                    getToggles()
                }
            }
        }
    }


    suspend fun getToggles(): ToggleResponse {
        val ctx: UnleashContext = unleashContext.value
        Log.d(TAG, "Fetching toggles with $ctx")
        val response = fetchToggles(ctx)
        if (response.isFetched()) {

            val toggles = response.config!!.toggles.groupBy { it.name }
                .mapValues { (_, v) -> v.first() }
            Log.d(TAG, "Fetched new state with ${toggles.size} toggles, emitting featuresReceivedFlow")
            featuresReceivedFlow.emit(UnleashState(ctx, toggles))
            return ToggleResponse(response.status, toggles)
        } else {
            if (response.isFailed()) {
                if (response.error is NotAuthorizedException) {
                    Log.e(TAG, "Not authorized to fetch toggles. Double check your SDK key")
                } else {
                    Log.e(TAG, "Failed to fetch toggles", response.error)
                }
            }
        }
        return ToggleResponse(response.status, error = response.error)
    }

    private suspend fun fetchToggles(ctx: UnleashContext): FetchResponse {
        val contextUrl = buildContextUrl(ctx)
        val request = Request.Builder().url(contextUrl)
                .headers(applicationHeaders.toHeaders())
        if (etag != null) {
            request.header("If-None-Match", etag!!)
        }
        try {
            val response = this.httpClient.newCall(request.build()).await()
            response.use { res ->
                Log.d(TAG, "Received status code ${res.code} from $contextUrl")
                return when {
                    res.isSuccessful -> {
                        etag = res.header("ETag")
                        res.body?.use { b ->
                            try {
                                val proxyResponse: ProxyResponse =
                                    Parser.jackson.readValue(b.string())
                                FetchResponse(Status.FETCHED, proxyResponse)
                            } catch (e: Exception) {
                                Log.w(TAG, "Couldn't parse data", e)
                                // If we fail to parse, just keep data
                                FetchResponse(Status.FAILED)
                            }
                        } ?: FetchResponse(Status.FAILED, error = NoBodyException())
                    }

                    res.code == 304 -> {
                        FetchResponse(Status.NOTMODIFIED)
                    }

                    res.code == 401 -> {
                        FetchResponse(Status.FAILED, error = NotAuthorizedException())
                    }

                    else -> {
                        FetchResponse(Status.FAILED, error = ServerException(res.code))
                    }
                }
            }
        } catch (e: IOException) {
            return FetchResponse(status = Status.FAILED, error = e)
        }
    }

    private suspend fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    // Don't bother with resuming the continuation if it is already cancelled.
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })

            continuation.invokeOnCancellation {
                try {
                    cancel()
                } catch (ex: Throwable) {
                    //Ignore cancel exception
                }
            }
        }
    }

    private fun buildContextUrl(ctx: UnleashContext): HttpUrl {
        var contextUrl = proxyUrl.newBuilder()
            .addQueryParameter("appName", appName)
        if (ctx.userId != null) {
            contextUrl.addQueryParameter("userId", ctx.userId)
        }
        if (ctx.remoteAddress != null) {
            contextUrl.addQueryParameter("remoteAddress", ctx.remoteAddress)
        }
        if (ctx.sessionId != null) {
            contextUrl.addQueryParameter("sessionId", ctx.sessionId)
        }
        ctx.properties.entries.forEach {
            contextUrl = contextUrl.addQueryParameter("properties[${it.key}]", it.value)
        }
        return contextUrl.build()
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdownNow()
        httpClient.connectionPool.evictAll()
        httpClient.cache?.closeQuietly()
    }
}
