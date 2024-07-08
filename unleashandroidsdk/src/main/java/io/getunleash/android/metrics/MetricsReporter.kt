package io.getunleash.android.metrics

interface MetricsReporter {

    suspend fun sendMetrics()
}
