package io.getunleash.android.io

import io.getunleash.android.data.Variant

interface MetricsHandler {
    fun registerEvaluated(toggleName: String, enabled: Boolean)
    fun registerVariantEvaluated(toggleName: String, variant: Variant)
    fun sendMetrics()
}