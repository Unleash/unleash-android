package io.getunleash.android.metrics

import android.util.Log
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Bucket
import io.getunleash.android.data.CountBucket
import io.getunleash.android.data.MetricsPayload
import io.getunleash.android.data.Parser
import io.getunleash.android.data.Variant
import io.getunleash.android.http.Throttler
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
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
    private val httpClient: OkHttpClient,
    private val applicationHeaders: Map<String, String> = config.getApplicationHeaders(config.metricsStrategy)
): MetricsCollector, MetricsReporter {
    companion object {
        private const val TAG: String = "MetricsSender"
    }
    private val metricsUrl = config.proxyUrl.toHttpUrl().newBuilder().addPathSegment("client")
        .addPathSegment("metrics").build()
    private var bucket: CountBucket = CountBucket(start = Date())
    private val throttler =
        Throttler(
            TimeUnit.MILLISECONDS.toSeconds(config.metricsStrategy.interval),
            longestAcceptableIntervalSeconds = 300,
            metricsUrl.toString()
        )

    override suspend fun sendMetrics() {
        if (bucket.isEmpty()) {
            Log.d(TAG, "No metrics to report")
            return
        }
        if (throttler.performAction()) {
            val toReport = swapAndFreeze()
            val payload = MetricsPayload(
                appName = config.appName,
                instanceId = config.instanceId,
                bucket = toReport
            )
            val request = Request.Builder()
                .headers(applicationHeaders.toHeaders())
                .url(metricsUrl).post(
                    Parser.jackson.writeValueAsString(payload)
                        .toRequestBody("application/json".toMediaType())
                ).build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.i(TAG, "Failed to report metrics for interval", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(
                        TAG,
                        "Received status code ${response.code} from ${request.method} $metricsUrl"
                    )
                    throttler.handle(response.code)
                    response.body.use { // Need to consume body to ensure we don't keep connection open
                    }
                }
            })
        } else {
            throttler.skipped()
        }
    }

    private fun swapAndFreeze(): Bucket {
        val bucketRef = bucket
        bucket = CountBucket(start = Date())
        return bucketRef.copy().toBucket(bucket.start)
    }

    override fun count(featureName: String, enabled: Boolean): Boolean {
        return bucket.count(featureName, enabled)
    }

    override fun countVariant(featureName: String, variant: Variant): Variant {
        return bucket.countVariant(featureName, variant)
    }
}
