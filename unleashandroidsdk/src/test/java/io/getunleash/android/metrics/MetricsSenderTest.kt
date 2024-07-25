package io.getunleash.android.metrics

import android.content.Context
import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Payload
import io.getunleash.android.data.Variant
import io.getunleash.android.http.ClientBuilder
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit
import net.javacrumbs.jsonunit.assertj.assertThatJson
import java.math.BigDecimal.valueOf

class MetricsSenderTest : BaseTest() {
    var server: MockWebServer  = MockWebServer()
    var proxyUrl: String = ""
    var configBuilder: UnleashConfig.Builder = UnleashConfig.newBuilder("my-test-app")
        .clientKey("some-key")
        .pollingStrategy.enabled(false)
        .localStorageConfig.enabled(false)

    @Before
    fun setUp() {
        server = MockWebServer()
        proxyUrl = server.url("proxy").toString()
        configBuilder = configBuilder.proxyUrl(proxyUrl)
    }

    @Test
    fun `does not push metrics if no metrics`() = runTest {
        val config = configBuilder.build()
        val httpClient = ClientBuilder(config, mock(Context::class.java)).build("test", config.metricsStrategy)
        val metricsSender = MetricsSender(config, httpClient)

        metricsSender.sendMetrics()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `pushes metrics if metrics`() = runTest {
        val config = configBuilder.build()
        val httpClient = ClientBuilder(config, mock(Context::class.java)).build("test", config.metricsStrategy)
        val metricsSender = MetricsSender(config, httpClient)

        metricsSender.count("feature1", true)
        metricsSender.count("feature2", false)
        metricsSender.countVariant(
            "feature2",
            Variant(
                "variant1",
                enabled = true,
                featureEnabled = false,
                Payload("string", "my variant")
            )
        )
        metricsSender.count("feature1", false)
        metricsSender.count("feature1", true)
        val today = java.time.LocalDate.now()
        metricsSender.sendMetrics()
        val request = server.takeRequest(
            1,
            TimeUnit.SECONDS
        )!!
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/proxy/client/metrics")
        val body = request.body.readUtf8()
        println(body)
        assertThatJson(body) {
            node("appName").isString().isEqualTo("my-test-app")
            node("instanceId").isString().matches(".+")
            node("bucket").apply {
                node("start").isString().matches("${today}T.+")
                node("stop").isString().matches("${today}T.+")
                node("toggles").apply {
                    node("feature1").apply {
                        node("yes").isNumber().isEqualTo(valueOf(2))
                        node("no").isNumber().isEqualTo(valueOf(1))
                        node("variants").isObject().isEqualTo(emptyMap<String, Any>())
                    }
                    node("feature2").apply {
                        node("yes").isNumber().isEqualTo(valueOf(0))
                        node("no").isNumber().isEqualTo(valueOf(1))
                        node("variants").apply {
                            node("variant1").isNumber().isEqualTo(valueOf(1))
                        }
                    }
                }
            }
        }
    }
}