package io.getunleash.android.metrics

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.getunleash.android.UnleashConfig
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.data.Bucket
import io.getunleash.android.data.EvaluationCount
import io.getunleash.android.data.MetricsPayload
import io.getunleash.android.data.Parser
import io.getunleash.android.data.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

class MetricsSender(
    private val config: UnleashConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(5000, TimeUnit.MILLISECONDS)
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .cache(
            Cache(
                directory = CacheDirectoryProvider().getCacheDirectory(),
                maxSize = 1024 * 1024 * 10
            )
        ).build()
) {
    private val tag: String = "MetricsSender"
    private val metricsUrl = config.proxyUrl.toHttpUrl().newBuilder().addPathSegment("client").addPathSegment("metrics").build()
    private var bucket: Bucket = Bucket(start = Date())

    suspend fun sendMetrics() {
        return withContext(Dispatchers.IO) {
            Log.i("MAIN", "Metrics sent!")
        }
    }

    fun reportMetrics() {
        val stop = Date()
        val bucketRef = bucket
        bucket = Bucket(start = stop)
        val clonedMetrics = bucketRef.copy(stop = stop)
        val payload = MetricsPayload(
            appName = config.appName,
            instanceId = config.instanceId ?: "not-set",
            bucket = clonedMetrics
        )
        val request = Request.Builder().header("Authorization", config.clientKey).url(metricsUrl).post(
            Parser.jackson.writeValueAsString(payload).toRequestBody("application/json".toMediaType())
        ).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i(tag, "Failed to report metrics for interval")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body.use { //Need to consume body to ensure we don't keep connection open
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun count(featureName: String, enabled: Boolean): Boolean {
        val count = if (enabled) {
            EvaluationCount(1, 0)
        } else {
            EvaluationCount(0, 1)
        }
        bucket.toggles.merge(featureName, count) { old: EvaluationCount?, new: EvaluationCount ->
            old?.copy(yes = old.yes + new.yes, no = old.no + new.no) ?: new
        }
        return enabled
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun countVariant(featureName: String, variant: Variant): Variant {
        bucket.toggles.compute(featureName) { _, count ->
            val evaluationCount = count ?: EvaluationCount(0, 0)
            evaluationCount.variants.merge(variant.name, 1) { old, value ->
                old + value
            }
            evaluationCount
        }
        return variant
    }
}