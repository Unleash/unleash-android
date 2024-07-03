package io.getunleash.android.polling

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.data.FetchResponse
import io.getunleash.android.data.Parser
import io.getunleash.android.data.ProxyResponse
import io.getunleash.android.data.Status
import io.getunleash.android.data.ToggleResponse
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.errors.NoBodyException
import io.getunleash.android.errors.NotAuthorizedException
import io.getunleash.errors.ServerException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Http Client for fetching data from Unleash Proxy.
 * By default creates an OkHttpClient with readTimeout set to 2 seconds and a cache of 10 MBs
 * @param httpClient - the http client to use for fetching toggles from Unleash proxy
 */
open class UnleashFetcher(
    private val proxyUrl: HttpUrl,
    private val applicationHeaders: Map<String, String> = emptyMap(),
    httpClientReadTimeout: Long = 5000,
    httpClientConnectionTimeout: Long = 2000,
    httpClientCacheSize: Long = 1024 * 1024 * 10,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(httpClientReadTimeout, TimeUnit.MILLISECONDS)
        .connectTimeout(httpClientConnectionTimeout, TimeUnit.MILLISECONDS)
        .cache(
            Cache(
                directory = CacheDirectoryProvider().getCacheDirectory(),
                maxSize = httpClientCacheSize
            )
        ).build()
) : Closeable {

    private val tag = "UnleashFetcher"
    private var etag: String? = null

    suspend fun getToggles(ctx: UnleashContext): ToggleResponse {
        Log.d(tag, "Fetching toggles with $ctx")
        val response = fetchToggles(ctx)
        return if (response.isFetched()) {
            ToggleResponse(
                status = response.status,
                toggles = response.config!!.toggles.groupBy { it.name }
                    .mapValues { (_, v) -> v.first() })
        } else {
            ToggleResponse(response.status, error = response.error)
        }
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
                Log.d(tag, "Received status code ${res.code} from $contextUrl")
                return when {
                    res.isSuccessful -> {

                        //TODO check why this, seems to be an internal cache, should still return the body
                        /*if (res.cacheResponse != null && res.networkResponse?.code == 304) {
                            //return FetchResponse(Status.NOTMODIFIED)
                        } else {*/
                            etag = res.header("ETag")
                            res.body?.use { b ->
                                try {
                                    val proxyResponse: ProxyResponse =
                                        Parser.jackson.readValue(b.string())
                                    FetchResponse(Status.FETCHED, proxyResponse)
                                } catch (e: Exception) {
                                    Log.w(tag, "Couldn't parse data", e)
                                    // If we fail to parse, just keep data
                                    FetchResponse(Status.FAILED)
                                }
                            } ?: FetchResponse(Status.FAILED, error = NoBodyException())
                        //}
                    }

                    res.code == 304 -> {
                        FetchResponse(Status.NOTMODIFIED)
                    }

                    res.code == 401 -> {
                        Log.e(tag, "Double check your SDK key")
                        FetchResponse(Status.FAILED, error = NotAuthorizedException())
                    }

                    else -> {
                        FetchResponse(Status.FAILED, error = ServerException(res.code))
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(tag, "An error occurred when fetching the latest configuration.", e)
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
        if (ctx.appName != null) {
            contextUrl.addQueryParameter("appName", ctx.appName)
        }
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
