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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeoutException

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
    private val fetcher: UnleashFetcher?

    init {
        eventListener?.let { addUnleashEventListener(it) }
        val metricsSender =
            if (unleashConfig.metricsStrategy.enabled) MetricsSender(unleashConfig) else NoOpMetrics()
        fetcher = if (unleashConfig.pollingStrategy.enabled) UnleashFetcher(
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
                    add(DataJob("fetchToggles", unleashConfig.pollingStrategy, fetcher::refreshToggles))
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

    override fun setContext(context: UnleashContext, timeout: Long) {
        unleashContextState.value = context
        runBlocking {
            try {
                withTimeout(timeout) {
                    cache.getUpdatesFlow()
                        .filter { it.context == context }
                        .first {
                            Log.i(TAG, "Unleash state is updated to $context")
                            true
                        }
                }
            } catch (e: TimeoutException) {
                Log.e(TAG, "Failed to update context", e)
            }
        }
    }

    override fun setContextAsync(context: UnleashContext) {
        unleashContextState.value = context
    }

    override fun getContext(): UnleashContext {
        return unleashContextState.value
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        unleashScope.launch {
            cache.getUpdatesFlow().collect {
                Log.i(TAG, "Cache updated, telling listeners to refresh")
                if (!ready) {
                    ready = true
                    listener.onReady()
                }
                // Q: We could propagate the state changed, does it make sense?
                listener.onStateChanged()
            }
        }
    }

    override fun close() {
        job.cancel("Unleash received closed signal")
    }
}
