package io.getunleash.android.metrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetricsSender private constructor(){
    suspend fun sendMetrics() {
        return withContext(Dispatchers.IO) {
            Log.i("MAIN", "Metrics sent!")
        }
    }

    companion object {
        @Volatile private var instance: MetricsSender? = null

        fun getInstance(): MetricsSender {
            return instance ?: synchronized(this) {
                instance ?: MetricsSender().also {
                    instance = it
                }
            }
        }
    }

}