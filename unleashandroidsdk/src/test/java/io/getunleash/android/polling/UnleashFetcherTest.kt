package io.getunleash.android.polling

import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.UnleashContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class UnleashFetcherTest : BaseTest() {

    @Test
    fun `should fetch toggles after initialization`() {
        // Given
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
            this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        // When
        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )
        unleashFetcher.startWatchingContext()

        // Then
        val request = server.takeRequest()
        assertThat(request).isNotNull
        assertThat(request.path).isEqualTo("/unleash?appName=test-app&userId=123")
        assertThat(request.method).isEqualTo("GET")  // or POST, PUT, etc.
    }

    @Test
    fun `changing the context should cancel in flight requests`() {
        // Given
        val server = MockWebServer()
        // a slow request
        server.enqueue(
            MockResponse()
                .setHeadersDelay(1000, TimeUnit.MILLISECONDS)
                .setBody(this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )

        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        // When
        val unleashFetcher = UnleashFetcher(
            UnleashConfig(
                server.url("unleash").toString(),
                "key-123",
                "test-app",
            ),
            OkHttpClient.Builder().build(),
            unleashContextState.asStateFlow()
        )

        runBlocking {
            launch {
                println("Setting context to 123")
                unleashFetcher.doFetchToggles(UnleashContext(userId = "123"))
            }
            delay(150)
            launch {
                println("Setting context to 321")
                unleashFetcher.doFetchToggles(UnleashContext(userId = "321"))
            }
        }

        // Then
        val firstRequest = server.takeRequest()
        assertThat(firstRequest.bodySize).isEqualTo(0)
        assertThat(firstRequest.path).isEqualTo("/unleash?appName=test-app&userId=123")

        val secondRequest = server.takeRequest()
        assertThat(secondRequest.path).isEqualTo("/unleash?appName=test-app&userId=321")
    }
}