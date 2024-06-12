package io.getunleash.unleashandroid.io

import io.getunleash.unleashandroid.data.Variant

interface MetricsHandler {
    fun registerEvaluated(toggleName: String, enabled: Boolean)
    fun registerVariantEvaluated(toggleName: String, variant: Variant)
    fun sendMetrics()
}