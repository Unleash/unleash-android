package io.getunleash.android.metrics

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class MetricsSenderWorker(val appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        try {
            TODO("MetricsSender.getInstance().sendMetrics()")
            return Result.success()
        } catch (e: Exception) {
            Log.e("MetricsSenderWorker", "Failed to send metrics", e)
            return Result.failure()
        }
    }
}