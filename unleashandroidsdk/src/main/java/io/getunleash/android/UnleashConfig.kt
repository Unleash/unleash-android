package io.getunleash.android

import io.getunleash.android.data.DataStrategy
import java.util.UUID

/**
 * Represents configuration for Unleash.
 * @property proxyUrl HTTP(s) URL to the Unleash Proxy (Required).
 * @property clientKey the key added as the Authorization header sent to the unleash-proxy (Required)
 * @property appName: name of the underlying application. Will be used as default in the [io.getunleash.android.data.UnleashContext] call (Required).
 * @property pollingStrategy How to poll for features. (Optional - Defaults to [io.getunleash.android.data.DataStrategy] with poll interval set to 60 seconds).
 * @property metricsStrategy How to poll for metrics. (Optional - Defaults to [io.getunleash.android.data.DataStrategy] with poll interval set to 60 seconds).
 */
data class UnleashConfig(
    val proxyUrl: String,
    val clientKey: String,
    val appName: String,
    val pollingStrategy: DataStrategy = DataStrategy(
        pauseOnBackground = true,
    ),
    val metricsStrategy: DataStrategy = DataStrategy(
        pauseOnBackground = true,
    ),
) {
    companion object {
        val instanceId: String = UUID.randomUUID().toString()
        /**
         * Get a [io.getunleash.android.UnleashConfig.Builder] with default values.
         */
        fun newBuilder(appName: String): Builder = Builder(appName)
    }

    val instanceId: String get() = Companion.instanceId

    fun getApplicationHeaders(strategy: DataStrategy): Map<String, String> {
        return strategy.httpCustomHeaders.plus(mapOf(
            "Authorization" to clientKey,
            "Content-Type" to "application/json",
            "UNLEASH-APPNAME" to appName,
            "User-Agent" to appName,
            "UNLEASH-INSTANCEID" to instanceId,
        ))
    }

    /**
     * Builder for [io.getunleash.android.UnleashConfig]
     */
    data class Builder(
        var appName: String,
        var proxyUrl: String? = null,
        var clientKey: String? = null
    ) {
        val pollingStrategy: DataStrategy.Builder = DataStrategy()
            .newBuilder(parent = this)
        val metricsStrategy: DataStrategy.Builder = DataStrategy()
            .newBuilder(parent = this)
        fun build(): UnleashConfig {
            if ((proxyUrl == null || clientKey == null) && (pollingStrategy.enabled || metricsStrategy.enabled)) {
                throw IllegalStateException("You must set the pollingStrategy and metricsStrategy to be disabled")
            }
            return UnleashConfig(
                proxyUrl = proxyUrl ?: "",
                clientKey = clientKey ?: "",
                appName = appName,
                pollingStrategy = pollingStrategy.build(),
                metricsStrategy = metricsStrategy.build(),
            )
        }

        fun proxyUrl(proxyUrl: String) = apply { this.proxyUrl = proxyUrl }
        fun clientKey(clientKey: String) = apply { this.clientKey = clientKey }
    }
}
