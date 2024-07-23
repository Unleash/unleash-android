package io.getunleash.android.polling

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Parser
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.errors.NoBodyException
import io.getunleash.android.errors.NotAuthorizedException
import io.getunleash.android.errors.ServerException
import io.getunleash.android.events.HeartbeatEvent
import io.getunleash.android.http.Throttler
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Http Client for fetching data from Unleash Proxy.
 * By default creates an OkHttpClient with readTimeout set to 2 seconds and a cache of 10 MBs
 * @param httpClient - the http client to use for fetching toggles from Unleash proxy
 */
open class UnleashFetcher(
    unleashConfig: UnleashConfig,
    private val httpClient: OkHttpClient,
    private val unleashContext: StateFlow<UnleashContext>,
) : Closeable {
    companion object {
        private const val TAG = "UnleashFetcher"
    }

    private val proxyUrl = unleashConfig.proxyUrl.toHttpUrl()
    private val applicationHeaders = unleashConfig.getApplicationHeaders(unleashConfig.pollingStrategy)
    private val appName = unleashConfig.appName
    private var etag: String? = null
    private val featuresReceivedFlow = MutableSharedFlow<UnleashState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val fetcherHeartbeatFlow = MutableSharedFlow<HeartbeatEvent>(
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val coroutineContextForContextChange: CoroutineContext = Dispatchers.IO
    private val currentCall = AtomicReference<Call?>(null)
    private val throttler =
        Throttler(
            TimeUnit.MILLISECONDS.toSeconds(unleashConfig.pollingStrategy.interval),
            longestAcceptableIntervalSeconds = 300,
            proxyUrl.toString()
        )

    fun getFeaturesReceivedFlow() = featuresReceivedFlow.asSharedFlow()

    fun startWatchingContext() {
        unleashScope.launch {
            unleashContext.distinctUntilChanged { old, new -> old != new }.collect {
                withContext(coroutineContextForContextChange) {
                    Log.d(TAG, "Unleash context changed: $it")
                    refreshToggles()
                }
            }
        }
    }

    suspend fun refreshToggles(): ToggleResponse {
        if (throttler.performAction()) {
            Log.d(TAG, "Refreshing toggles")
            val response = refreshTogglesWithContext(unleashContext.value)
            fetcherHeartbeatFlow.emit(HeartbeatEvent(response.status, response.error?.message))
            return response
        }
        Log.i(TAG, "Skipping refresh toggles due to throttling")
        fetcherHeartbeatFlow.emit(HeartbeatEvent(Status.THROTTLED))
        return ToggleResponse(Status.THROTTLED)
    }

    internal suspend fun refreshTogglesWithContext(ctx: UnleashContext): ToggleResponse {
        val response = fetchToggles(ctx)
        if (response.isSuccess()) {

            val toggles = response.config!!.toggles.groupBy { it.name }
                .mapValues { (_, v) -> v.first() }
            Log.d(
                TAG,
                "Fetched new state with ${toggles.size} toggles, emitting featuresReceivedFlow"
            )
            featuresReceivedFlow.emit(UnleashState(ctx, toggles))
            return ToggleResponse(response.status, toggles)
        } else {
            if (response.isFailed()) {
                if (response.error is NotAuthorizedException) {
                    Log.e(TAG, "Not authorized to fetch toggles. Double check your SDK key")
                } else {
                    Log.i(TAG, "Failed to fetch toggles ${response.error?.message}", response.error)
                }
            }
        }
        return ToggleResponse(response.status, error = response.error)
    }

    private suspend fun fetchToggles(ctx: UnleashContext): FetchResponse {
        val contextUrl = buildContextUrl(ctx)
        try {
            val request = Request.Builder().url(contextUrl)
                .headers(applicationHeaders.toHeaders())
            if (etag != null) {
                request.header("If-None-Match", etag!!)
            }
            val call = this.httpClient.newCall(request.build())
            val inFlightCall = currentCall.get()
            if (!currentCall.compareAndSet(inFlightCall, call)) {
                return FetchResponse(
                    Status.FAILED,
                    error = IllegalStateException("Failed to set new call while ${inFlightCall?.request()?.url} is in flight")
                )
            } else if (inFlightCall != null && !inFlightCall.isCanceled()) {
                Log.d(
                    TAG,
                    "Cancelling previous ${inFlightCall.request().method} ${inFlightCall.request().url}"
                )
                inFlightCall.cancel()
            }

            Log.d(TAG, "Fetching toggles from $contextUrl")
            val response = call.await()
            response.use { res ->
                Log.d(TAG, "Received status code ${res.code} from $contextUrl")
                throttler.handle(response.code)
                return when {
                    res.isSuccessful -> {
                        etag = res.header("ETag")
                        res.body?.use { b ->
                            try {
                                val proxyResponse: ProxyResponse =
                                    Parser.jackson.readValue(b.string())
                                FetchResponse(Status.SUCCESS, proxyResponse)
                            } catch (e: Exception) {
                                // If we fail to parse, just keep data
                                FetchResponse(Status.FAILED, error = e)
                            }
                        } ?: FetchResponse(Status.FAILED, error = NoBodyException())
                    }

                    res.code == 304 -> {
                        FetchResponse(Status.NOT_MODIFIED)
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

    fun getHeartbeatFlow(): SharedFlow<HeartbeatEvent> {
        return fetcherHeartbeatFlow.asSharedFlow()
    }
}
