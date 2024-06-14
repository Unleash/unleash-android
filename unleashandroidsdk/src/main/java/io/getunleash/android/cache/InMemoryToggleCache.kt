package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.TogglesReceivedListener
import java.util.concurrent.ConcurrentHashMap

class InMemoryToggleCache : ToggleCache, TogglesReceivedListener {
    private val internalCache = ConcurrentHashMap<String, Map<String, Toggle>>()

    override fun read(key: String): Map<String, Toggle> {
        return internalCache[key] ?: emptyMap()
    }

    override fun write(key: String, value: Map<String, Toggle>) {
        internalCache[key] = value
    }

    override fun onTogglesReceived(toggles: Map<String, Toggle>) {

    }
}