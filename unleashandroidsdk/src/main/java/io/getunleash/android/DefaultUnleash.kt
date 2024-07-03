package io.getunleash.android

import android.util.Log
import io.getunleash.android.cache.InMemoryToggleCache
import io.getunleash.android.cache.ObservableCache
import io.getunleash.android.cache.ObservableToggleCache
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener
import io.getunleash.android.tasks.LifecycleAwareTaskManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val tag = "Unleash"
const val supportedSpecVersion = "4.3.0"

val unleashExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.e(tag, "Caught unhandled exception: ${exception.message}", exception)
}

private val job = SupervisorJob()
val unleashScope = CoroutineScope(Dispatchers.Default + job + unleashExceptionHandler)

class DefaultUnleash(
    unleashConfig: UnleashConfig,
    unleashContext: UnleashContext = UnleashContext(),
    cacheImpl: ToggleCache = InMemoryToggleCache()
) : Unleash {
    private val unleashContextState = MutableStateFlow(unleashContext)
    private val taskManager = LifecycleAwareTaskManager(unleashConfig, unleashContextState)
    private val cache: ObservableToggleCache = ObservableCache(cacheImpl)

    init {
        try {
            cache.subscribeTo(taskManager.getFeaturesReceivedFlow())

            taskManager.startForegroundJobs()
        } catch (e: Exception) {
            Log.e(tag, "Error initializing Unleash", e)
        }
    }

    override fun isEnabled(toggleName: String, defaultValue: Boolean): Boolean {
        Log.d(tag, "UNLEASH Checking if $toggleName is enabled")
        return cache.get(toggleName)?.enabled ?: defaultValue // TODO metricsReporter.log(toggleName,  enabled ?: defaultValue)
    }

    override fun getVariant(toggleName: String, defaultValue: Variant) : Variant {
        val variant =
            if (isEnabled(toggleName)) {
                cache.get(toggleName)?.variant ?: defaultValue
            } else {
                defaultValue
            }
        return variant // TODO metricsReporter.logVariant(toggleName, variant)
    }

    override fun setContext(context: UnleashContext) {
        // important, this should not force an HTTP call but put an event to do
        // that at some point in the nearish future so we don't get DDoS'd
        unleashContextState.value = context
    }

    override fun getContext(): UnleashContext {
        return unleashContextState.value
    }

    override fun setMetricsStrategy(strategy: DataStrategy) {
        TODO("Not yet implemented")
    }

    override fun setFlagFetchStrategy(strategy: DataStrategy) {
        TODO("Not yet implemented")
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        // TODO split into different listener methods
        unleashScope.launch {
            cache.getUpdatesFlow().collect {
                Log.i(tag, "Cache updated, telling listeners to refresh")
                listener.onRefresh()
            }
        }

        unleashScope.launch {
            unleashContextState.asStateFlow().collect {
                Log.i(tag, "Context updated, telling listeners to refresh")
                listener.onRefresh()
            }
        }
    }

    override fun close() {
        job.cancel("Unleash received closed signal")
    }
}
