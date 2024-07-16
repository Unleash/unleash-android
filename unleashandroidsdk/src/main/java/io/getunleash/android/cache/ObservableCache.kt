package io.getunleash.android.cache

import android.util.Log
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState
import io.getunleash.android.unleashScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObservableCache(private val cache: ToggleCache, private val coroutineScope: CoroutineScope = unleashScope) : ObservableToggleCache {
    companion object {
        private const val TAG = "ObservableCache"
    }

    private var events = MutableSharedFlow<UnleashState>()
    override fun read(): Map<String, Toggle> {
        return cache.read()
    }

    override fun get(key: String): Toggle? {
        return cache.get(key)
    }

    override fun write(state: UnleashState) {
        cache.write(state)
        Log.d(TAG, "Done writing cache")
        coroutineScope.launch {
            Log.d(TAG, "Emitting new state with ${state.toggles.size} toggles for ${state.context}")
            events.emit(state)
        }
        Log.d(TAG, "Done sending event")
    }

    override fun subscribeTo(featuresReceived: Flow<UnleashState>) {
        Log.d(TAG, "Subscribing to observable cache")
        coroutineScope.launch {
            featuresReceived.collect { state ->
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Storing new state with ${state.toggles.size} toggles for $state.context")
                    write(state)
                }
            }
        }
    }

    override fun getUpdatesFlow(): Flow<UnleashState> {
        return events.asSharedFlow()
    }
}
