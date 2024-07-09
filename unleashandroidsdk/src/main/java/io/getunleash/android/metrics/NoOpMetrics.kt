package io.getunleash.android.metrics

import io.getunleash.android.data.Variant

class NoOpMetrics: MetricsCollector, MetricsReporter {
    override fun count(featureName: String, enabled: Boolean): Boolean {
        return enabled
    }

    override fun countVariant(featureName: String, variant: Variant): Variant {
        return variant
    }

    override suspend fun sendMetrics() {
    }
}