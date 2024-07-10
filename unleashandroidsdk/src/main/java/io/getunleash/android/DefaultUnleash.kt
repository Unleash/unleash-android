package io.getunleash.android

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.getunleash.android.cache.InMemoryToggleCache
import io.getunleash.android.cache.ObservableCache
import io.getunleash.android.cache.ObservableToggleCache
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener
import io.getunleash.android.metrics.MetricsCollector
import io.getunleash.android.metrics.MetricsSender
import io.getunleash.android.metrics.NoOpMetrics
import io.getunleash.android.polling.UnleashFetcher
import io.getunleash.android.tasks.DataJob
import io.getunleash.android.tasks.LifecycleAwareTaskManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

val unleashExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.e("UnleashHandler", "Caught unhandled exception: ${exception.message}", exception)
}

private val job = SupervisorJob()
val unleashScope = CoroutineScope(Dispatchers.Default + job + unleashExceptionHandler)

class DefaultUnleash(
    private val unleashConfig: UnleashConfig,
    unleashContext: UnleashContext = UnleashContext(),
    cacheImpl: ToggleCache = InMemoryToggleCache(),
    eventListener: UnleashEventListener? = null,
    lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle
) : Unleash {
    companion object {
        private const val TAG = "Unleash"
    }

    private val unleashContextState = MutableStateFlow(unleashContext)
    private val metrics: MetricsCollector
    private val taskManager: LifecycleAwareTaskManager
    private val cache: ObservableToggleCache = ObservableCache(cacheImpl)
    private var ready = false

    init {
        eventListener?.let { addUnleashEventListener(it) }
        val metricsSender =
            if (unleashConfig.metricsStrategy.enabled) MetricsSender(unleashConfig) else NoOpMetrics()
        val fetcher = if (unleashConfig.pollingStrategy.enabled) UnleashFetcher(
            unleashContextState.asStateFlow(),
            unleashConfig.appName,
            unleashConfig.proxyUrl.toHttpUrl(),
            unleashConfig.buildHttpClient(unleashConfig.pollingStrategy),
            unleashConfig.getApplicationHeaders(unleashConfig.pollingStrategy)
        ) else null
        metrics = metricsSender
        taskManager = LifecycleAwareTaskManager(
            buildList {
                if (fetcher != null) {
                    add(DataJob("fetchToggles", unleashConfig.pollingStrategy, fetcher::getToggles))
                }
                if (unleashConfig.metricsStrategy.enabled) {
                    add(
                        DataJob(
                            "sendMetrics",
                            unleashConfig.metricsStrategy,
                            metricsSender::sendMetrics
                        )
                    )
                }
            }
        )
        lifecycle.addObserver(taskManager)
        if (fetcher != null) {
            cache.subscribeTo(fetcher.getFeaturesReceivedFlow())
        }
    }

    override fun isEnabled(toggleName: String, defaultValue: Boolean): Boolean {
        Log.d(TAG, "UNLEASH Checking if $toggleName is enabled")
        val enabled = cache.get(toggleName)?.enabled ?: defaultValue
        metrics.count(toggleName, enabled)
        return enabled
    }

    override fun getVariant(toggleName: String, defaultValue: Variant): Variant {
        val variant =
            if (isEnabled(toggleName)) {
                cache.get(toggleName)?.variant ?: defaultValue
            } else {
                defaultValue
            }
        metrics.countVariant(toggleName, variant)
        return variant
    }

    override fun setContext(context: UnleashContext) {
        // important, this should not force an HTTP call but put an event to do
        // that at some point in the nearish future so we don't get DDoS'd
        unleashContextState.value = context
    }

    override fun getContext(): UnleashContext {
        return unleashContextState.value
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        // TODO split into different listener methods
        unleashScope.launch {
            cache.getUpdatesFlow().collect {
                Log.i(TAG, "Cache updated, telling listeners to refresh")
                if (!ready) {
                    ready = true
                    listener.onReady()
                }
                listener.onStateChanged()
            }
        }
    }

    override fun close() {
        job.cancel("Unleash received closed signal")
    }
}
