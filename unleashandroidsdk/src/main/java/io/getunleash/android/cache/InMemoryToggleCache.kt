package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import java.util.concurrent.ConcurrentHashMap

class InMemoryToggleCache : ToggleCache {
    private val internalCache = ConcurrentHashMap<String, Map<String, Toggle>>()

    override fun read(key: String): Map<String, Toggle> {
        return internalCache[key] ?: emptyMap()
    }

    override fun write(key: String, value: Map<String, Toggle>) {
        internalCache[key] = value
    }
}