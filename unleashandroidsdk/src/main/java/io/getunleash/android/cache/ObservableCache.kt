package io.getunleash.android.cache

import android.util.Log
import io.getunleash.android.data.Toggle
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObservableCache(private val cache: ToggleCache) : ObservableToggleCache {
    private val tag = "ObservableCache"

    private var events = MutableSharedFlow<Unit>()
    override fun read(): Map<String, Toggle> {
        return cache.read()
    }

    override fun get(key: String): Toggle? {
        return cache.get(key)
    }

    override fun write(value: Map<String, Toggle>) {
        cache.write(value)
        unleashScope.launch {
            events.emit(Unit)
        }
    }

    override fun subscribeTo(featuresReceived: Flow<Map<String, Toggle>>) {
        Log.d(tag, "Subscribing cache, subscribers: ${events.subscriptionCount.value}")
        unleashScope.launch {
            featuresReceived.collect { toggles ->
                withContext(Dispatchers.IO) {
                    Log.d(tag, "Received new state with ${toggles.size} toggles $toggles")
                    write(toggles)
                }
            }
        }
    }

    override fun getUpdatesFlow(): Flow<Unit> {
        return events.asSharedFlow()
    }
}