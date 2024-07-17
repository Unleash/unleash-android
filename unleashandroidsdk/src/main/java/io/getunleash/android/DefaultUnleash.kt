package io.getunleash.android

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.getunleash.android.backup.LocalBackup
import io.getunleash.android.cache.CacheDirectoryProvider
import io.getunleash.android.cache.InMemoryToggleCache
import io.getunleash.android.cache.ObservableCache
import io.getunleash.android.cache.ObservableToggleCache
import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener
import io.getunleash.android.http.ClientBuilder
import io.getunleash.android.http.NetworkStatusHelper
import io.getunleash.android.metrics.MetricsCollector
import io.getunleash.android.metrics.MetricsReporter
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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.internal.toImmutableList
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

val unleashExceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.e("UnleashHandler", "Caught unhandled exception: ${exception.message}", exception)
}

private val job = SupervisorJob()
val unleashScope = CoroutineScope(Dispatchers.Default + job + unleashExceptionHandler)

class DefaultUnleash(
    private val androidContext: Context,
    private val unleashConfig: UnleashConfig,
    unleashContext: UnleashContext = UnleashContext(),
    cacheImpl: ToggleCache = InMemoryToggleCache(),
    eventListener: UnleashEventListener? = null,
    lifecycle: Lifecycle = getLifecycle(androidContext)
) : Unleash {
    companion object {
        private const val TAG = "Unleash"
    }

    private val unleashContextState = MutableStateFlow(unleashContext)
    private val metrics: MetricsCollector
    private val taskManager: LifecycleAwareTaskManager
    private val cache: ObservableToggleCache = ObservableCache(cacheImpl)
    private var ready = AtomicBoolean(false)
    private val readyFlow = MutableStateFlow(false)
    private val fetcher: UnleashFetcher?
    private val networkStatusHelper = NetworkStatusHelper(androidContext)

    init {
        eventListener?.let { addUnleashEventListener(it) }
        val httpClientBuilder = ClientBuilder(unleashConfig, androidContext)
        val metricsSender =
            if (unleashConfig.metricsStrategy.enabled)
                MetricsSender(
                    unleashConfig,
                    httpClientBuilder.build("metrics", unleashConfig.metricsStrategy)
                )
            else NoOpMetrics()
        metrics = metricsSender
        fetcher = if (unleashConfig.pollingStrategy.enabled)
            UnleashFetcher(
                unleashConfig,
                httpClientBuilder.build("poller", unleashConfig.pollingStrategy),
                unleashContextState.asStateFlow()
            ) else null
        taskManager = LifecycleAwareTaskManager(
            dataJobs = buildDataJobs(fetcher, metricsSender),
            networkAvailable = networkStatusHelper.isAvailable()
        )
        networkStatusHelper.registerNetworkListener(taskManager)
        if (unleashConfig.localStorageConfig.enabled) {
            val localBackup = getLocalBackup()
            localBackup.subscribeTo(cache.getUpdatesFlow())
        }
        fetcher?.let {
            it.startWatchingContext()
            cache.subscribeTo(it.getFeaturesReceivedFlow())
        }
        lifecycle.addObserver(taskManager)
    }

    private fun buildDataJobs(fetcher: UnleashFetcher?, metricsSender: MetricsReporter) = buildList {
        if (fetcher != null) {
            add(
                DataJob(
                    "fetchToggles",
                    unleashConfig.pollingStrategy,
                    fetcher::refreshToggles
                )
            )
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
    }.toImmutableList()

    private fun getLocalBackup(): LocalBackup {
        val backupDir = CacheDirectoryProvider(unleashConfig.localStorageConfig, androidContext)
            .getCacheDirectory("unleash_backup")
        val localBackup = LocalBackup(backupDir)
        unleashScope.launch {
            withContext(Dispatchers.IO) {
                unleashContextState.asStateFlow().takeWhile { !ready.get() }.collect { ctx ->
                    Log.d(TAG, "Loading state from backup for $ctx")
                    localBackup.loadFromDisc(unleashContextState.value)?.let { state ->
                        if (!ready.get()) {
                            Log.i(TAG, "Loaded state from backup for $ctx")
                            cache.write(state)
                        } else {
                            Log.d(TAG, "Ignoring backup, Unleash is already ready")
                        }
                    }
                }
            }
        }
        return localBackup
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

    override fun refreshTogglesNow() {
        runBlocking {
            fetcher?.refreshToggles()
        }
    }

    override fun refreshTogglesNowAsync() {
        unleashScope.launch {
            withContext(Dispatchers.IO) {
                fetcher?.refreshToggles()
            }
        }
    }

    override fun setContext(context: UnleashContext) {
        unleashContextState.value = context
        refreshTogglesNow()
    }

    @Throws(TimeoutException::class)
    override fun setContextWithTimeout(context: UnleashContext, timeout: Long) {
        unleashContextState.value = context
        runBlocking {
            withTimeout(timeout) {
                fetcher?.refreshToggles()
            }
        }
    }

    override fun setContextAsync(context: UnleashContext) {
        unleashContextState.value = context
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        unleashScope.launch {
            cache.getUpdatesFlow().collect {
                if (ready.compareAndSet(false, true)) {
                    readyFlow.value = true
                }
                Log.d(TAG, "Cache updated, notifying $listener that state changed")
                listener.onStateChanged()
            }
        }
        unleashScope.launch {
            readyFlow.asStateFlow().filter { it }.collect {
                Log.d(TAG, "Ready state changed, notifying $listener")
                listener.onReady()
            }
        }
    }

    override fun close() {
        networkStatusHelper.close()
        job.cancel("Unleash received closed signal")
    }
}

private fun getLifecycle(androidContext: Context) =
    if (androidContext is LifecycleOwner) {
        Log.d("Unleash", "Using lifecycle from Android context")
        androidContext.lifecycle
    } else {
        Log.d("Unleash", "Using lifecycle from ProcessLifecycleOwner")
        ProcessLifecycleOwner.get().lifecycle
    }
