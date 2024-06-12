package io.getunleash.unleashandroid.data

import java.util.Date
import java.util.concurrent.ConcurrentHashMap

data class EvaluationCount(
    var yes: Int,
    var no: Int,
    val variants: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
)

data class Bucket(
    val start: Date,
    var stop: Date? = null,
    val toggles: ConcurrentHashMap<String, EvaluationCount> = ConcurrentHashMap()
)

data class MetricsPayload(
    val appName: String, val instanceId: String, val environment: String, val bucket: Bucket
)