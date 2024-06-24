package io.getunleash.android.cache

import io.getunleash.android.Events
import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.TogglesReceivedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class InMemoryToggleCache : ToggleCache, CoroutineScope by CoroutineScope(
    Dispatchers.Default + SupervisorJob())  {
    private val internalCache = ConcurrentHashMap<String, Map<String, Toggle>>()

    override fun read(key: String): Map<String, Toggle> {
        return internalCache[key] ?: emptyMap()
    }

    override fun write(key: String, value: Map<String, Toggle>) {
        internalCache[key] = value
        launch(Dispatchers.IO) {
            Events.broadcastToggleStoreUpdated(value)
        }
    }

    override fun onTogglesReceived(toggles: Map<String, Toggle>) {
        write("toggles", toggles)
    }

    fun destroy() {
        cancel()  // Cancel the CoroutineScope when the ToggleManager is being destroyed
    }
}
