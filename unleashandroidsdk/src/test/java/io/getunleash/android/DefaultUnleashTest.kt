package io.getunleash.android

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState
import io.getunleash.android.events.UnleashEventListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
    @Test
    fun testDefaultUnleash() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            cacheImpl = testCache,
            lifecycle = mock(Lifecycle::class.java),
        )
        assertThat(unleash.isEnabled("feature1")).isTrue()
        assertThat(unleash.isEnabled("feature2")).isFalse()
        assertThat(unleash.isEnabled("nonExisting")).isFalse()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addingOnReadyListenersShouldNotifyAll() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )

        var onReady1 = false
        var onReady2 = false
        var onReady3 = false

        // adding listeners before start should not be overridden
        unleash.addUnleashEventListener(object: UnleashEventListener {
            override fun onReady() {
                onReady1 = true
            }
        })

        // listeners added at start should also be called
        unleash.start(
            listOf(object : UnleashEventListener {
                override fun onReady() {
                    onReady2 = true
                }
            },
                object : UnleashEventListener {
                    override fun onReady() {
                        onReady3 = true
                    }
                })
        )

        while (!onReady1 || !onReady2 || !onReady3) {
            runCurrent()
        }
    }
}