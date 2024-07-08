package io.getunleash.android.metrics

import io.getunleash.android.data.Variant

interface MetricsCollector {

    @RequiresApi(Build.VERSION_CODES.N)
    fun count(featureName: String, enabled: Boolean): Boolean
    @RequiresApi(Build.VERSION_CODES.N)
    fun countVariant(featureName: String, variant: Variant): Variant
}
