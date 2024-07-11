package io.getunleash.android.polling

import io.getunleash.android.BaseTest
import io.getunleash.android.data.UnleashContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            unleashContextState.asStateFlow(),
            "test-app",
            server.url("unleash"),
            OkHttpClient.Builder().build()
        )

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
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
                .setBodyDelay(5, TimeUnit.SECONDS)
        )
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText())
        )

        val unleashContextState = MutableStateFlow(UnleashContext(userId = "123"))

        // When
        val unleashFetcher = UnleashFetcher(
            unleashContextState.asStateFlow(),
            "test-app",
            server.url("unleash"),
            OkHttpClient.Builder().build()
        )
        runBlocking {
            delay(10)
        }
        unleashContextState.value = UnleashContext(userId = "321")

        // Then
        val firstRequest = server.takeRequest()

        val secondRequest = server.takeRequest()
        assertThat(secondRequest.path).isEqualTo("/unleash?appName=test-app&userId=321")
    }
}