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
import io.getunleash.android.data.ImpressionEvent
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashFetcherHeartbeatListener
import io.getunleash.android.events.UnleashImpressionEventListener
import io.getunleash.android.events.UnleashListener
import io.getunleash.android.events.UnleashReadyListener
import io.getunleash.android.events.UnleashStateListener
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    eventListeners: List<UnleashListener> = emptyList(),
    private val lifecycle: Lifecycle = getLifecycle(androidContext),
    private val coroutineScope: CoroutineScope = unleashScope
) : Unleash {
    companion object {
        private const val TAG = "Unleash"
    }

    private val unleashContextState = MutableStateFlow(unleashContext)
    private val metrics: MetricsCollector
    private val taskManager: LifecycleAwareTaskManager
    private val cache: ObservableToggleCache = ObservableCache(cacheImpl, coroutineScope)
    private var started = AtomicBoolean(false)
    private var ready = AtomicBoolean(false)
    private val fetcher: UnleashFetcher?
    private val networkStatusHelper = NetworkStatusHelper(androidContext)
    private val impressionEventsFlow = MutableSharedFlow<ImpressionEvent>(
        replay = 1,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
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
            networkAvailable = networkStatusHelper.isAvailable(),
            scope = coroutineScope
        )
        if (!unleashConfig.delayedInitialization) {
            start(eventListeners)
        } else if (eventListeners.isNotEmpty()) {
            throw IllegalArgumentException("Event listeners are not supported as constructor arguments with delayed initialization")
        }
    }

    fun start(
        eventListeners: List<UnleashListener> = emptyList(),
        bootstrap: List<Toggle> = emptyList()
    ) {
        if (!started.compareAndSet(false, true)) {
            Log.w(TAG, "Unleash already started, ignoring start call")
            return
        }
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
        eventListeners.forEach { addUnleashEventListener(it) }
        if (bootstrap.isNotEmpty()) {
            Log.i(TAG, "Using provided bootstrap toggles")
            cache.write(UnleashState(unleashContextState.value, bootstrap.associateBy { it.name }))
        }
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
        coroutineScope.launch {
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
        val toggle = cache.get(toggleName)
        val enabled = toggle?.enabled ?: defaultValue
        val impressionData = toggle?.impressionData ?: unleashConfig.forceImpressionData
        if (impressionData) {
            emit(ImpressionEvent(toggleName, enabled, unleashContextState.value))
        }
        metrics.count(toggleName, enabled)
        return enabled
    }

    override fun getVariant(toggleName: String, defaultValue: Variant): Variant {
        val toggle = cache.get(toggleName)
        val enabled = isEnabled(toggleName)
        val variant = if (enabled) (toggle?.variant ?: defaultValue) else defaultValue
        val impressionData = toggle?.impressionData ?: unleashConfig.forceImpressionData
        if (impressionData) {
            emit(ImpressionEvent(toggleName, enabled, unleashContextState.value, variant.name))
        }
        metrics.countVariant(toggleName, variant)
        return variant
    }

    private fun emit(impressionEvent: ImpressionEvent) {
        coroutineScope.launch {
            impressionEventsFlow.emit(impressionEvent)
        }
    }

    override fun refreshTogglesNow() {
        runBlocking {
            fetcher?.refreshToggles()
        }
    }

    override fun refreshTogglesNowAsync() {
        coroutineScope.launch {
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

    override fun addUnleashEventListener(listener: UnleashListener) {

        if (listener is UnleashReadyListener) coroutineScope.launch {
            cache.getUpdatesFlow().first{
                true
            }
            if (ready.compareAndSet(false, true)) {
                Log.d(TAG, "Unleash state changed to ready")
            }
            Log.d(TAG, "Notifying UnleashReadyListener")
            listener.onReady()
        }

        if (listener is UnleashStateListener) coroutineScope.launch {
            cache.getUpdatesFlow().collect {
                listener.onStateChanged()
            }
        }

        if (listener is UnleashImpressionEventListener) coroutineScope.launch {
            impressionEventsFlow.asSharedFlow().collect { event ->
                listener.onImpression(event)
            }
        }

        if (fetcher != null && listener is UnleashFetcherHeartbeatListener) coroutineScope.launch {
            fetcher.getHeartbeatFlow().collect { event ->
                if (event.status.isFailed()) {
                    listener.onError(event)
                } else if (event.status.isNotModified()) {
                    listener.togglesChecked()
                } else if (event.status.isSuccess()) {
                    listener.togglesUpdated()
                }
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
