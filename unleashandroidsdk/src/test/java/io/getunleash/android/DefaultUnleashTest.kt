package io.getunleash.android

import androidx.lifecycle.Lifecycle
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

class DefaultUnleashTest: BaseTest() {
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

        override fun write(state: UnleashState) {
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