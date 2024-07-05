package io.getunleash.android

import io.getunleash.android.data.DataStrategy
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * Represents configuration for Unleash.
 * @property proxyUrl HTTP(s) URL to the Unleash Proxy (Required).
 * @property clientKey the key added as the Authorization header sent to the unleash-proxy (Required)
 * @property appName: name of the underlying application. Will be used as default in the [io.getunleash.UnleashContext] call (Required).
 * @property instanceId instance id of your client
 * @property httpClientReadTimeout How long to wait for HTTP reads in milliseconds. (Optional - Defaults to 5000)
 * @property httpClientConnectionTimeout How long to wait for HTTP connection in milliseconds. (Optional - Defaults to 2000)
 * @property httpClientCacheSize Disk space (in bytes) set aside for http cache. (Optional - Defaults to 10MB)
 * @property reportMetrics Should the client collate and report metrics? The [io.getunleah.ReportMetrics] dataclass includes a metricsInterval field which defaults to 60 seconds. (Optional - defaults to null)
 */
data class UnleashConfig(
    val proxyUrl: String,
    val clientKey: String,
    val appName: String? = "unleash-android-sdk",
    val instanceId: String? = UUID.randomUUID().toString(),
    val httpClientConnectionTimeout: Long = 2000,
    val httpClientReadTimeout: Long = 5000,
    val httpClientCacheSize: Long = 1024 * 1024 * 10,
    val httpClientCustomHeaders: Map<String, String> = emptyMap(),
    val pollingStrategy: DataStrategy = DataStrategy(60000,
        respectHibernation = true,
    ),
    val metricsStrategy: DataStrategy = DataStrategy(60000,
        respectHibernation = true,
    ),
) {
    /**
     * Get a [io.getunleash.UnleashConfig.Builder] with all fields set to the value of
     * this instance of the class.
     */
    fun newBuilder(): Builder =
        Builder(
            proxyUrl = proxyUrl,
            clientKey = clientKey,
            appName = appName,
            httpClientConnectionTimeout = httpClientConnectionTimeout,
            httpClientReadTimeout = httpClientReadTimeout,
            httpClientCacheSize = httpClientCacheSize,
            enableMetrics = metricsStrategy.enabled,
            metricsInterval = metricsStrategy.interval
        )

    fun getApplicationHeaders(): Map<String, String> {
        return httpClientCustomHeaders.plus(mapOf(
            "Authorization" to clientKey,
            "Content-Type" to "application/json",
            "UNLEASH-APPNAME" to appName!!,
            "User-Agent" to appName,
            "UNLEASH-INSTANCEID" to instanceId!!,
        ))
    }

    companion object {
        /**
         * Get a [io.getunleash.UnleashConfig.Builder] with no fields set.
         */
        fun newBuilder(): Builder = Builder()
    }

    /**
     * Builder for [io.getunleash.UnleashConfig]
     */
    data class Builder(
        var proxyUrl: String? = null,
        var clientKey: String? = null,
        var appName: String? = null,
        var httpClientConnectionTimeout: Long? = null,
        var httpClientReadTimeout: Long? = null,
        var httpClientCacheSize: Long? = null,
        var enableMetrics: Boolean = false,
        var metricsHttpClient: OkHttpClient? = null,
        var metricsInterval: Long? = null,
        var instanceId: String? = null,
    ) {
        fun proxyUrl(proxyUrl: String) = apply { this.proxyUrl = proxyUrl }

        @Deprecated(message = "use clientKey(key: String) instead", replaceWith = ReplaceWith("clientKey(key: String)"))
        fun clientSecret(secret: String) = apply { this.clientKey = secret }
        fun clientKey(key: String) = apply { this.clientKey = key }
        fun appName(appName: String) = apply { this.appName = appName }
        fun httpClientConnectionTimeout(timeoutInMs: Long) = apply { this.httpClientConnectionTimeout = timeoutInMs }
        fun httpClientConnectionTimeoutInSeconds(seconds: Long) = apply { this.httpClientConnectionTimeout = seconds * 1000 }
        fun httpClientReadTimeout(timeoutInMs: Long) = apply { this.httpClientReadTimeout = timeoutInMs }
        fun httpClientReadTimeoutInSeconds(seconds: Long) = apply { this.httpClientReadTimeout = seconds * 1000 }
        fun httpClientCacheSize(cacheSize: Long) = apply { this.httpClientCacheSize = cacheSize }
        fun enableMetrics() = apply { this.enableMetrics = true }
        fun disableMetrics() = apply { this.enableMetrics = false }
        fun metricsHttpClient(client: OkHttpClient) = apply { this.metricsHttpClient = client }
        fun metricsInterval(intervalInMs: Long) = apply { this.metricsInterval = intervalInMs }
        fun metricsIntervalInSeconds(seconds: Long) = apply { this.metricsInterval = seconds * 1000 }
        fun instanceId(id: String) = apply { this.instanceId = id }
        fun build(): UnleashConfig = UnleashConfig(
            proxyUrl = proxyUrl ?: throw IllegalStateException("You have to set proxy url in your UnleashConfig"),
            clientKey = clientKey
                ?: throw IllegalStateException("You have to set client key in your UnleashConfig"),
            appName = appName,
            httpClientConnectionTimeout = httpClientConnectionTimeout ?: 2000,
            httpClientReadTimeout = httpClientReadTimeout ?: 5000,
            httpClientCacheSize = httpClientCacheSize ?: (1024 * 1024 * 10),
            metricsStrategy = DataStrategy(
                interval = metricsInterval ?: 60000,
                enabled = enableMetrics
            ),
            instanceId = instanceId
        )

    }
}
