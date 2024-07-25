package io.getunleash.android.metrics

import io.getunleash.android.data.Variant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class EvaluationCount(
    var yes: Int,
    var no: Int,
    val variants: MutableMap<String, Int> = mutableMapOf()
)

data class Bucket(
    val start: Date,
    val stop: Date,
    val toggles: MutableMap<String, EvaluationCount> = mutableMapOf()
)

interface UnleashMetricsBucket {
    fun count(featureName: String, enabled: Boolean): Boolean
    fun countVariant(featureName: String, variant: Variant): Variant
    fun isEmpty(): Boolean
}

data class CountBucket(
    val start: Date = Date(),
    val yes: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
    val no: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
    val variants: ConcurrentHashMap<Pair<String, String>, AtomicInteger> = ConcurrentHashMap()
): UnleashMetricsBucket {

    override fun count(featureName: String, enabled: Boolean): Boolean {
        (if (enabled) yes else no)
            .getOrPut(featureName) { AtomicInteger(0) }.incrementAndGet()
        return enabled
    }

    override fun countVariant(featureName: String, variant: Variant): Variant {
        variants.getOrPut(Pair(featureName, variant.name)) { AtomicInteger(0) }.incrementAndGet()
        return variant
    }

    override fun isEmpty(): Boolean {
        return yes.isEmpty() && no.isEmpty() && variants.isEmpty()
    }

    fun toBucket(until: Date = Date()): Bucket {
        val bucket = Bucket(start, until)
        for ((feature, count) in yes) {
            bucket.toggles[feature] = EvaluationCount(count.get(), 0)
        }
        for ((feature, count) in no) {
            bucket.toggles.getOrPut(feature) { EvaluationCount(0, 0) }.no = count.get()

        }
        for ((pair, count) in variants) {
            bucket.toggles.getOrPut(pair.first) { EvaluationCount(0, 0) }.variants[pair.second] = count.get()
        }
        return bucket
    }
}

data class MetricsPayload(
    val appName: String, val instanceId: String, val bucket: Bucket
)
