package io.getunleash.android.metrics

import io.getunleash.android.data.Variant

interface MetricsCollector {

    fun count(featureName: String, enabled: Boolean): Boolean
    fun countVariant(featureName: String, variant: Variant): Variant
}
