package io.getunleash.android.tasks

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.NetworkType
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.polling.UnleashFetcher
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val TAG = "TaskManager"

class LifecycleAwareTaskManager(
    private val unleashConfig: UnleashConfig,
    private val unleashContext: StateFlow<UnleashContext>,
) : LifecycleEventObserver {
    private var foregroundMetricsSender: Job? = null
    private var featureTogglesPoller: Job? = null
    private var isRunning = false

    private val featuresReceivedFlow = MutableSharedFlow<Map<String, Toggle>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val fetcher = UnleashFetcher(
        unleashConfig.proxyUrl.toHttpUrl(),
        unleashConfig.getApplicationHeaders()
    )

    fun getFeaturesReceivedFlow(): SharedFlow<Map<String, Toggle>> = featuresReceivedFlow.asSharedFlow()

    init {
        Log.i("MAIN", "Trying to get lifecycle")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // listen to unleash context state changes
        unleashScope.launch {
            unleashContext.collect {
                withContext(Dispatchers.IO) {
                    Log.i("MAIN", "Unleash context changed: $it")
                    doFetchToggles()
                }
            }
        }
    }

    fun startForegroundJobs() {
        if (isRunning) return
        isRunning = true
        println("Starting foreground jobs")

        if (unleashConfig.reportMetrics != null) {
            foregroundMetricsSender = unleashScope.launch {
                withContext(Dispatchers.IO) {
                    while (isActive) {
                        //MetricsSender.getInstance().sendMetrics()
                        println("TODO: MetricsSender.getInstance().sendMetrics()")
                        delay(timeMillis = unleashConfig.reportMetrics.metricsInterval)
                    }
                }
            }
        }

        featureTogglesPoller = unleashScope.launch {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    doFetchToggles()
                    delay(timeMillis = unleashConfig.pollingIntervalInMs)
                }
            }
        }
    }

    private suspend fun doFetchToggles() {
        val response = fetcher.getToggles(unleashContext.value)
        if (response.isFetched()) {
            Log.d(TAG, "Fetched new state with ${response.toggles.size} toggles, emitting featuresReceivedFlow")
            featuresReceivedFlow.emit(
                response.toggles
            )
        }
    }

    private fun stopForegroundJobs() {
        if (!isRunning) return
        isRunning = false
        foregroundMetricsSender?.cancel()
        featureTogglesPoller?.cancel()
        flushMetricsOnBackground()
    }

    private fun flushMetricsOnBackground() {
        // TODO this might not work as MetricsSenderWorker instance should create
        // a new instance. This is likely to prevent memory leaks by not keeping
        // references to existing instances.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        Log.i("MAIN", "Trying to enqueue flushMetricsJob")

        /*val flushMetricsJob = OneTimeWorkRequestBuilder<MetricsSenderWorker>()
            .setConstraints(constraints)
            .setInitialDelay(250, TimeUnit.MILLISECONDS)
            .build()

        backgroundWorkManager.enqueueUniqueWork(
            "backgroundSendMetrics",
            ExistingWorkPolicy.REPLACE,
            flushMetricsJob
        )*/
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d("MAIN", "Lifecycle state changed: $event")
        when (event) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> startForegroundJobs()
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> stopForegroundJobs()
            Lifecycle.Event.ON_DESTROY -> {
                stopForegroundJobs()
            }
            else -> {}
        }
    }
}