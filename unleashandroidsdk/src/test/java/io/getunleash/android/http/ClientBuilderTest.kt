package io.getunleash.android.http

import android.content.Context
import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class ClientBuilderTest : BaseTest() {

        @Test
        fun `when local storage is enabled it uses a client cache`() {
            val config =
                UnleashConfig.newBuilder("my-app")
                    .proxyUrl("https://localhost:4242/proxy")
                    .localStorageConfig.dir(createTempDirectory("cbt1").toFile().path)
                    .clientKey("some-key").build()

            val clientBuilder = ClientBuilder(config, mock(Context::class.java))
            val client = clientBuilder.build("clientName", config.pollingStrategy)

            assertThat(client.cache).isNotNull()
        }


    @Test
    fun `when local storage is disabled it does not use a client cache`() {
        val config =
            UnleashConfig.newBuilder("my-app")
                .proxyUrl("https://localhost:4242/proxy")
                .localStorageConfig.enabled(false)
                .clientKey("some-key").build()

        val clientBuilder = ClientBuilder(config, mock(Context::class.java))
        val client = clientBuilder.build("clientName", config.pollingStrategy)

        assertThat(client.cache).isNull()
    }

    @Test
    fun `when http client is provided by customer we should use it`() {
        val builder = spy(OkHttpClient.Builder())
        val customHttpClient = mock(OkHttpClient::class.java)
        `when`(customHttpClient.newBuilder()).thenReturn(builder)
        val config =
            UnleashConfig.newBuilder("my-app")
                .proxyUrl("https://localhost:4242/proxy")
                .httpClient(customHttpClient)
                .localStorageConfig.enabled(false)
                .clientKey("some-key").build()

        val clientBuilder = ClientBuilder(config, mock(Context::class.java))
        clientBuilder.build("clientName", config.pollingStrategy)

        verify(builder).readTimeout(5000, TimeUnit.MILLISECONDS)
        verify(builder).writeTimeout(5000, TimeUnit.MILLISECONDS)
        verify(builder).connectTimeout(2000, TimeUnit.MILLISECONDS)
    }
}