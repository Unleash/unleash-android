package io.getunleash.android.tasks

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

data class DataJob(val id: String, val strategy: DataStrategy, val action: suspend () -> Unit)

class LifecycleAwareTaskManager(
    private val dataJobs: List<DataJob>
) : LifecycleEventObserver {
    companion object {
        private const val TAG = "TaskManager"
    }
    private val foregroundWorkers = mutableMapOf<String, Job>()
    private var isRunning = false

    init {
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
