package io.getunleash.android.polling

import io.getunleash.android.BaseTest
import io.getunleash.android.data.Status
import io.getunleash.android.data.UnleashContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
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
    fun `changing the context should cancel in flight requests`() = runTest {
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
        runBlocking {
            val unleashFetcher = UnleashFetcher(
                unleashContextState.asStateFlow(),
                "test-app",
                server.url("unleash"),
                OkHttpClient.Builder().build()
            )
            // refresh toggles to force first request
            val firstResponse = unleashFetcher.refreshToggles()
            println("Setting context to 321")
            unleashContextState.value = UnleashContext(userId = "321")

            // Then
            val firstRequest = server.takeRequest()
            assertThat(firstRequest.path).isEqualTo("/unleash?appName=test-app&userId=123")
            assertThat(firstResponse.status).isEqualTo(Status.FAILED)
            assertThat(firstResponse.error).isInstanceOf(IOException::class.java)
            val secondRequest = server.takeRequest(450, TimeUnit.MILLISECONDS)
            assertThat(secondRequest?.path).isEqualTo("/unleash?appName=test-app&userId=321")
        }
    }
}