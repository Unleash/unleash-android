package io.getunleash.android.http

import android.content.Context
import io.getunleash.android.UnleashConfig
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.data.DataStrategy
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ClientBuilder(private val unleashConfig: UnleashConfig, private val androidContext: Context) {
    fun build(
        clientName: String,
        strategy: DataStrategy
    ): OkHttpClient {
        // either use the provided client or create a new one
        val clientBuilder = unleashConfig.httpClient?.newBuilder() ?: OkHttpClient.Builder()
        val builder = clientBuilder
            .readTimeout(strategy.httpReadTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(strategy.httpWriteTimeout, TimeUnit.MILLISECONDS)
            .connectTimeout(strategy.httpConnectionTimeout, TimeUnit.MILLISECONDS)
        if (unleashConfig.localStorageConfig.enabled) {
            builder.cache(
                Cache(
                    directory = CacheDirectoryProvider(
                        unleashConfig.localStorageConfig,
                        androidContext
                    ).getCacheDirectory(
                        "unleash_${clientName}_http_cache", true
                    ),
                    maxSize = strategy.httpCacheSize
                )
            )
        }
        return builder.build()
    }
}