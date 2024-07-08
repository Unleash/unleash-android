package io.getunleash.android

import io.getunleash.android.data.Bucket
import io.getunleash.android.data.CountBucket
import io.getunleash.android.data.Variant
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Date


class CountBucketTest {
    private val iterations = 500

    private val featureNames = (1..11).map { "feature$it" }
    private val variants = (1..7).map { "variant$it" }
    private val randomEnabled = (1..iterations).map { it % 3 == 0 }

    @Test
    fun compareNewMethodWithOldMethod() {
        val start = Date()
        val countBucket = CountBucket(start)
        val bucket = Bucket(start)
        for (i in 0 until iterations) {
            val feature = featureNames[i % featureNames.size]
            countBucket.count(feature, randomEnabled[i % randomEnabled.size])
            bucket.count(feature, randomEnabled[i % randomEnabled.size])
            if (i % 5 > 2) {
                val variant = variants[(i*i) % variants.size]
                countBucket.countVariant(feature, Variant(variant))
                bucket.countVariant(feature, Variant(variant))
            }
        }
        assertThat(countBucket.toBucket()).isEqualTo(bucket)
        assertThat(bucket.toggles["feature1"]!!.yes).isEqualTo(15)
        assertThat(bucket.toggles["feature1"]!!.no).isEqualTo(31)
        assertThat(bucket.toggles["feature1"]!!.variants).isEqualTo(mapOf(
            "variant1" to 2,
            "variant2" to 5,
            "variant3" to 5,
            "variant5" to 6
        ))
    }

    @Test
    fun countBucketCountsProperly() {
        val start = Date()
        val countBucket = CountBucket(start)
        val feature = "feature1"
        countBucket.count(feature, true)
        countBucket.countVariant(feature, Variant("variant1"))
        countBucket.count(feature, true)
        countBucket.countVariant(feature, Variant("variant2"))
        countBucket.count(feature, false)
        countBucket.countVariant(feature, Variant("disabled"))
        countBucket.count(feature, true)
        countBucket.countVariant(feature, Variant("variant2"))
        countBucket.count(feature, false)
        countBucket.countVariant(feature, Variant("disabled"))

        val bucket = countBucket.toBucket()
        assertThat(bucket.toggles["feature1"]!!.yes).isEqualTo(3)
        assertThat(bucket.toggles["feature1"]!!.no).isEqualTo(2)
        assertThat(bucket.toggles["feature1"]!!.variants).isEqualTo(mapOf(
            "variant1" to 1,
            "variant2" to 2,
            "disabled" to 2
        ))
    }
}