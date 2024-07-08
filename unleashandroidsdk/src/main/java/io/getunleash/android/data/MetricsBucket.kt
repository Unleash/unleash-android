package io.getunleash.android.data

import android.os.Build
import androidx.annotation.RequiresApi
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
): UnleashMetricsBucket {

    @RequiresApi(Build.VERSION_CODES.N)
    @Deprecated("Old method use CountBucket instead")
    override fun count(featureName: String, enabled: Boolean): Boolean {
        val count = if (enabled) {
            EvaluationCount(1, 0)
        } else {
            EvaluationCount(0, 1)
        }
        toggles.merge(featureName, count) { old: EvaluationCount?, new: EvaluationCount ->
            old?.copy(yes = old.yes + new.yes, no = old.no + new.no) ?: new
        }
        return enabled
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Deprecated("Old method use CountBucket instead")
    override fun countVariant(featureName: String, variant: Variant): Variant {
        toggles.compute(featureName) { _, count ->
            val evaluationCount = count ?: EvaluationCount(0, 0)
            evaluationCount.variants.merge(variant.name, 1) { old, value ->
                old + value
            }
            evaluationCount
        }
        return variant
    }
}

interface UnleashMetricsBucket {
    fun count(featureName: String, enabled: Boolean): Boolean
    fun countVariant(featureName: String, variant: Variant): Variant
}

data class CountBucket(
    val start: Date = Date(),
    var stop: Date? = null,
    val yes: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val no: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val variants: ConcurrentHashMap<Pair<String, String>, Int> = ConcurrentHashMap()
): UnleashMetricsBucket {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun count(featureName: String, enabled: Boolean): Boolean {
        if (enabled) {
            yes.compute(featureName) { _, value ->
                (value ?: 0) + 1
            }
        } else {
            no.compute(featureName) { _, value ->
                (value ?: 0) + 1
            }
        }
        return enabled
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun countVariant(featureName: String, variant: Variant): Variant {
        variants.compute(Pair(featureName, variant.name)) {_, value ->
            (value ?: 0) + 1
        }
        return variant
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun toBucket(): Bucket {
        val bucket = Bucket(start, stop)
        for ((feature, count) in yes) {
            bucket.toggles[feature] = EvaluationCount(count, 0)
        }
        for ((feature, count) in no) {
            bucket.toggles.getOrPut(feature) { EvaluationCount(0, 0) }.no = count

        }
        for ((pair, count) in variants) {
            bucket.toggles.compute(pair.first) { _, evaluationCount ->
                val evaluation = evaluationCount ?: EvaluationCount(0, 0)
                evaluation.variants[pair.second] = count
                evaluation
            }
        }
        return bucket
    }
}

data class MetricsPayload(
    val appName: String, val instanceId: String, val bucket: Bucket
)