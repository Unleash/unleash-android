package io.getunleash.android.tasks

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.getunleash.android.UnleashConfig
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.metrics.MetricsReporter
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
import kotlin.coroutines.CoroutineContext

data class DataJob(val id: String, val strategy: DataStrategy, val action: suspend () -> Unit)

class LifecycleAwareTaskManager(
    private val unleashConfig: UnleashConfig,
    private val unleashContext: StateFlow<UnleashContext>,
    private val metricsReporter: MetricsReporter,
    private val fetcher: UnleashFetcher = UnleashFetcher(
        unleashConfig.proxyUrl.toHttpUrl(),
        unleashConfig.buildHttpClient(unleashConfig.pollingStrategy),
        unleashConfig.getApplicationHeaders(unleashConfig.pollingStrategy)
    ),
    private val coroutineContextForContextChange: CoroutineContext = Dispatchers.IO
) : LifecycleEventObserver {
    companion object {
        private const val TAG = "TaskManager"
    }

    private val dataJobs: List<DataJob>
    private val foregroundWorkers = mutableMapOf<String, Job>()
    private var isRunning = false

    private val featuresReceivedFlow = MutableSharedFlow<Map<String, Toggle>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun getFeaturesReceivedFlow(): SharedFlow<Map<String, Toggle>> = featuresReceivedFlow.asSharedFlow()

    init {
        val taskManager = this
        dataJobs = buildList {
            if (unleashConfig.pollingStrategy.enabled) {
                add(DataJob("fetchToggles", unleashConfig.pollingStrategy, taskManager::doFetchToggles))
            }
            if (unleashConfig.metricsStrategy.enabled) {
                add(DataJob("sendMetrics", unleashConfig.metricsStrategy, metricsReporter::sendMetrics))
            }
        }

        // listen to unleash context state changes
        unleashScope.launch {
            unleashContext.collect {
                withContext(coroutineContextForContextChange) {
                    Log.d(TAG, "Unleash context changed: $it")
                    doFetchToggles()
                }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }


    private fun startForegroundJobs(
        coroutineContext: CoroutineContext = Dispatchers.IO
    ) {
        if (!isRunning) {
            isRunning = true

            dataJobs.forEach { dataJob ->
                if (foregroundWorkers[dataJob.id]?.isActive != true) {
                    Log.d(TAG, "Starting foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id] = startWithStrategy(
                        dataJob.strategy,
                        coroutineContext,
                        dataJob.action
                    )
                }
            }
        }
    }

    private fun stopForegroundJobs() {
        if (isRunning) {
            isRunning = false

            dataJobs.forEach { dataJob ->
                if (dataJob.strategy.pauseOnBackground) {
                    Log.d(TAG, "Pausing foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id]?.cancel()
                } else {
                    Log.d(TAG, "Keeping job running: ${dataJob.id}")
                }
            }
        }
    }

    private fun startWithStrategy(
        strategy: DataStrategy,
        context: CoroutineContext,
        action: suspend () -> Unit
    ): Job {
        return unleashScope.launch {
            withContext(context) {
                while (isActive) {
                    if (strategy.delay > 0) {
                        delay(strategy.delay)
                    }
                    action()
                    delay(timeMillis = strategy.interval)
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

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d(TAG, "Lifecycle state changed: $event")
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
