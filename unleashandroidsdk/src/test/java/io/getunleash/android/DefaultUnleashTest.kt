package io.getunleash.android

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.ImpressionEvent
import io.getunleash.android.data.Status
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.events.HeartbeatEvent
import io.getunleash.android.events.UnleashFetcherHeartbeatListener
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
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultUnleashTest : BaseTest() {
    private val staticToggleList = listOf(
        Toggle(name = "feature1", enabled = true),
        Toggle(name = "feature2", enabled = false),
    )
    private val testCache = object : ToggleCache {
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
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
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
        unleash.addUnleashEventListener(object : UnleashReadyListener {
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

    @Test
    fun `validate we can read featureEnabled from the variants`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
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

        var ready = false
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                ready = true
            }
        })
        unleash.start()

        await().atMost(2, TimeUnit.SECONDS).until { ready }
        val variant = unleash.getVariant("AwesomeDemo")
        assertThat(variant).isNotNull
        assertThat(variant.enabled).isTrue()
        assertThat(variant.featureEnabled).isTrue()
        assertThat(variant.name).isEqualTo("black")
    }

    @Test
    fun `can listen to heartbeat events when polling`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(304)
        )
        server.enqueue(
            MockResponse().setResponseCode(500)
        )
        server.enqueue(
            MockResponse().setResponseCode(304)
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
            unleashContext = UnleashContext(userId = "1"),
            lifecycle = mock(Lifecycle::class.java),
        )

        var togglesUpdated = 0
        var togglesChecked = 0
        var togglesThrottled = 0
        var togglesFailed = 0

        unleash.start(eventListeners = listOf(object : UnleashFetcherHeartbeatListener {
            override fun togglesUpdated() {
                togglesUpdated++
            }

            override fun togglesChecked() {
                togglesChecked++
            }

            override fun onError(event: HeartbeatEvent) {
                if (event.status == Status.THROTTLED) togglesThrottled++ else togglesFailed++
            }
        }))

        await().atMost(2, TimeUnit.SECONDS).until {
            togglesUpdated > 0
        }
        // change context to force a refresh
        unleash.setContext(UnleashContext(userId = "2"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesChecked > 0
        }
        unleash.setContext(UnleashContext(userId = "3"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesFailed > 0
        }
        // too fast request after an error should be throttled
        unleash.setContext(UnleashContext(userId = "4"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesThrottled > 0
        }

        assertThat(togglesUpdated).isEqualTo(1)
        assertThat(togglesChecked).isEqualTo(1)
        assertThat(togglesFailed).isEqualTo(1)
        assertThat(togglesThrottled).isEqualTo(1)
    }


    @Test
    fun `if unleash is not started, setting context does not poll, until start is called`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
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

        unleash.setContext(UnleashContext(userId = "123"))
        Thread.sleep(100)
        assertThat(server.requestCount).isEqualTo(0)

        var ready = false
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                ready = true
            }
        })
        unleash.start()

        await().atMost(2, TimeUnit.SECONDS).until { ready }
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().requestUrl?.queryParameter("userId")).isEqualTo("123")
    }

    @Test
    fun `can load from disk using a backup`() {
        val sampleBackupResponse = File(this::class.java.classLoader?.getResource("sample-response.json")!!.path)
        val inspectionableCache = object : ToggleCache {
            var toggles: Map<String, Toggle> = emptyMap()
            override fun read(): Map<String, Toggle> {
                TODO("Not needed")
            }

            override fun get(key: String): Toggle? {
                TODO("Not needed")
            }

            override fun write(state: UnleashState) {
                toggles = state.toggles
            }

        }
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
            cacheImpl = inspectionableCache
        )

        unleash.start(bootstrapFile = sampleBackupResponse)

        await().atMost(2, TimeUnit.SECONDS).until { inspectionableCache.toggles.isNotEmpty() }
        assertThat(inspectionableCache.toggles).hasSize(8)
        val aToggle = inspectionableCache.toggles["AwesomeDemo"]
        assertThat(aToggle).isNotNull
        assertThat(aToggle!!.enabled).isTrue()
        assertThat(aToggle.variant).isNotNull
        assertThat(aToggle.variant.name).isEqualTo("black")
    }

}
