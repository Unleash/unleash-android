package io.getunleash.android.http

import android.content.Context
import io.getunleash.android.BaseTest
import io.getunleash.android.UnleashConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
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
}