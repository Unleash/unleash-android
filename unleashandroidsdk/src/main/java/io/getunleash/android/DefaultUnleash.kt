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
import io.getunleash.android.data.DataStrategy
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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.internal.toImmutableList
import java.util.concurrent.TimeUnit
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
    private val cache: ObservableToggleCache
    private var ready = AtomicBoolean(false)
    private val fetcher: UnleashFetcher?

    init {
        val metricsSender =
            if (unleashConfig.metricsStrategy.enabled)
                MetricsSender(
                    unleashConfig,
                    buildHttpClient("metrics", androidContext, unleashConfig.metricsStrategy)
                )
            else NoOpMetrics()
        fetcher = if (unleashConfig.pollingStrategy.enabled)
            UnleashFetcher(
                unleashConfig,
                buildHttpClient("poller", androidContext, unleashConfig.pollingStrategy),
                unleashContextState.asStateFlow()
            ) else null
        metrics = metricsSender
        taskManager = LifecycleAwareTaskManager(
            buildList {
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
        )
        cache = ObservableCache(cacheImpl)
        if (unleashConfig.localStorageConfig.enabled) {
            val localBackup = loadFromBackup(cacheImpl, eventListener)
            localBackup.subscribeTo(cache.getUpdatesFlow())
        }
        if (fetcher != null) {
            fetcher.startWatchingContext()
            cache.subscribeTo(fetcher.getFeaturesReceivedFlow())
        }
        lifecycle.addObserver(taskManager)
        eventListener?.let { addUnleashEventListener(it) }
    }

    private fun loadFromBackup(
        cacheImpl: ToggleCache,
        eventListener: UnleashEventListener?
    ): LocalBackup {
        val backupDir = CacheDirectoryProvider(unleashConfig.localStorageConfig, androidContext)
            .getCacheDirectory("unleash_backup")
        val localBackup = LocalBackup(backupDir)
        unleashScope.launch {
            withContext(Dispatchers.IO) {
                unleashContextState.asStateFlow().takeWhile { !ready.get() }.collect { ctx ->
                    Log.d(TAG, "Loading state from backup for $ctx")
                    localBackup.loadFromDisc(unleashContextState.value)?.let { state ->
                        if (ready.compareAndSet(false, true)) {
                            Log.i(TAG, "Loaded state from backup for $ctx")
                            cacheImpl.write(state)
                            eventListener?.onReady()
                        } else {
                            Log.d(TAG, "Ignoring backup, Unleash is already ready")
                        }
                    }
                }
            }
        }
        return localBackup
    }

    private fun buildHttpClient(
        clientName: String,
        androidContext: Context,
        strategy: DataStrategy
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(strategy.httpReadTimeout, TimeUnit.MILLISECONDS)
            .connectTimeout(strategy.httpConnectionTimeout, TimeUnit.MILLISECONDS)
            .cache(
                Cache(
                    directory = CacheDirectoryProvider(
                        unleashConfig.localStorageConfig,
                        androidContext
                    ).getCacheDirectory(
                        "unleash_${clientName}_http_cache", true
                    ),
                    maxSize = strategy.httpCacheSize
                )
            ).build()
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

    override fun getContext(): UnleashContext {
        return unleashContextState.value
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        unleashScope.launch {
            cache.getUpdatesFlow().collect {
                if (ready.compareAndSet(false, true)) {
                    Log.i(TAG, "Toggles received, Unleash is ready")
                    listener.onReady()
                }
                Log.d(TAG, "Cache updated, notifying listeners that state changed")
                listener.onStateChanged()
            }
        }
    }

    override fun close() {
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
