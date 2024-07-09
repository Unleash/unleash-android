package io.getunleash.android

import androidx.lifecycle.Lifecycle
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.Toggle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest= Config.NONE, sdk = [21], shadows = [ShadowLog::class])
class DefaultUnleashTest {
    private val testCache = object: ToggleCache {
        val staticToggles = mapOf(
            "feature1" to Toggle(name = "feature1", enabled = true),
            "feature2" to Toggle(name = "feature2", enabled = false),
        )
        override fun read(): Map<String, Toggle> {
            return staticToggles
        }

        override fun get(key: String): Toggle? {
            return staticToggles[key]
        }

        override fun write(value: Map<String, Toggle>) {
            TODO("Should not be used")
        }

    }
    private val unleash = DefaultUnleash(
        unleashConfig = UnleashConfig.newBuilder("test-android-app")
            .pollingStrategy.enabled(false)
            .metricsStrategy.enabled(false)
            .build(),
        cacheImpl = testCache,
        lifecycle = mock(Lifecycle::class.java),
    )

    @Test
    fun testDefaultUnleash() {
        assertThat(unleash.isEnabled("feature1")).isTrue()
        assertThat(unleash.isEnabled("feature2")).isFalse()
        assertThat(unleash.isEnabled("nonExisting")).isFalse()
    }
}