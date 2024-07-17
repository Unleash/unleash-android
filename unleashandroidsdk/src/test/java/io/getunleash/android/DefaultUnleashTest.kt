package io.getunleash.android

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.ImpressionEvent
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.events.UnleashImpressionEventListener
import io.getunleash.android.events.UnleashReadyListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.awaitility.Awaitility.await
import org.junit.Test
import org.mockito.Mockito.mock
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

class DefaultUnleashTest: BaseTest() {
    private val staticToggleList = listOf(
        Toggle(name = "feature1", enabled = true),
        Toggle(name = "feature2", enabled = false),
    )
    private val testCache = object: ToggleCache {
        val staticToggles = staticToggleList.associateBy { it.name }
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
    fun testDefaultUnleashWithStaticCache() {
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

    @Test
    fun testDefaultUnleashWithBootstrap() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )
        unleash.start(bootstrap = staticToggleList)
        assertThat(unleash.isEnabled("feature1")).isTrue()
        assertThat(unleash.isEnabled("feature2")).isFalse()
        assertThat(unleash.isEnabled("nonExisting")).isFalse()
    }

    @Test
    fun `adding on ready listeners should notify all`() {
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
        unleash.addUnleashEventListener(object: UnleashReadyListener {
            override fun onReady() {
                onReady1 = true
            }
        })

        // listeners added at start should also be called
        unleash.start(
            listOf(object : UnleashReadyListener {
                override fun onReady() {
                    onReady2 = true
                }
            },
                object : UnleashReadyListener {
                    override fun onReady() {
                        onReady3 = true
                    }
                })
        )

        await().atMost(1, TimeUnit.SECONDS).until {
            onReady1 && onReady2 && onReady3
        }
    }

    @Test
    fun `initializing client twice should show a console warning`() {
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

        unleash.start()
        unleash.start()

        assertThat(ShadowLog.getLogs())
            .map(ShadowLog.LogItem::msg)
            .contains(Tuple("Unleash already started, ignoring start call"))
    }

    @Test
    fun `feature with impression event set to true will emit an impression event`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java)
        )

        var ready = false
        val impressionEvents = mutableListOf<ImpressionEvent>()
        unleash.start(
            eventListeners = listOf(object : UnleashReadyListener, UnleashImpressionEventListener {
                override fun onImpression(event: ImpressionEvent) {
                    impressionEvents.add(event)
                }

                override fun onReady() {
                    ready = true
                }
            }), bootstrap = listOf(
                Toggle(name = "with-impression-1", enabled = true, impressionData = true),
                Toggle(name = "with-impression-2", enabled = true, impressionData = true),
                Toggle(name = "with-impression-3", enabled = true, impressionData = true),
                Toggle(name = "without-impression", enabled = false)
            )
        )
        await().atMost(1, TimeUnit.SECONDS).until { ready }


        unleash.isEnabled("with-impression-1")
        unleash.isEnabled("with-impression-2", true)
        unleash.isEnabled("without-impression")
        unleash.isEnabled("with-impression-3", false)
        unleash.isEnabled("non-existing-toggle")

        await().atMost(1, TimeUnit.SECONDS).until { impressionEvents.size >= 3 }
        assertThat(impressionEvents).hasSize(3)
        for (i in 1 until 4) {
            impressionEvents[i - 1].let {
                assertThat(it.featureName).isEqualTo("with-impression-$i")
                assertThat(it.enabled).isTrue()
            }
        }
    }
}