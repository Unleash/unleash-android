package io.getunleash.android.tasks

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.unleashScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

data class DataJob(val id: String, val strategy: DataStrategy, val action: suspend () -> Unit)

class LifecycleAwareTaskManager(
    private val dataJobs: List<DataJob>,
    private val scope: CoroutineScope = unleashScope,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : LifecycleEventObserver {
    companion object {
        private const val TAG = "TaskManager"
    }
    internal val foregroundWorkers = mutableMapOf<String, Job>()
    private var isForeground = false
    private var isDestroying = false

    internal fun startForegroundJobs() {
        if (!isForeground) {
            isForeground = true

            dataJobs.forEach { dataJob ->
                if (foregroundWorkers[dataJob.id]?.isActive != true) {
                    Log.d(TAG, "Starting foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id] = startWithStrategy(
                        dataJob.id,
                        dataJob.strategy,
                        dataJob.action
                    )
                }
            }
        }
    }

    private fun stopForegroundJobs() {
        if (isForeground || isDestroying) {
            isForeground = false

            dataJobs.forEach { dataJob ->
                if (dataJob.strategy.pauseOnBackground) {
                    Log.d(TAG, "Pausing foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id]?.cancel()
                    Log.d(TAG, "Job is active: ${foregroundWorkers[dataJob.id]?.isActive}")
                } else {
                    Log.d(TAG, "Keeping job running: ${dataJob.id}")
                }
            }
        }
    }

    private fun startWithStrategy(
        id: String,
        strategy: DataStrategy,
        action: suspend () -> Unit
    ): Job {
        Log.d(TAG, "Launching job $id")
        return scope.launch {
            Log.d(TAG, "Inside job $id in context $ioContext")
            withContext(ioContext) {
                Log.d(TAG, "Within $ioContext for job $id")
                while (!isDestroying && (isForeground || !strategy.pauseOnBackground)) {
                    if (strategy.delay > 0) {
                        delay(strategy.delay)
                    }
                    Log.d(TAG, "[$id] Executing action $isForeground")
                    action()
                    Log.d(TAG, "[$id] Delaying for ${strategy.interval}ms")
                    delay(timeMillis = strategy.interval)
                    Log.d(TAG, "[$id] Done waiting ${strategy.interval}ms")
                }
            }
        }
    }

    fun stop() {
        isDestroying = true
        stopForegroundJobs()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d(TAG, "Lifecycle state changed: $event")
        when (event) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> startForegroundJobs()
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> stopForegroundJobs()
            Lifecycle.Event.ON_DESTROY -> {
                isDestroying = true
                stopForegroundJobs()
            }
            else -> {}
        }
    }
}
