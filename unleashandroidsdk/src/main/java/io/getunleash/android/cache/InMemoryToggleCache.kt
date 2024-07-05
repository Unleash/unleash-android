package io.getunleash.android.cache

import io.getunleash.android.data.Toggle

class InMemoryToggleCache : ToggleCache {
    @Volatile
    private var internalCache = emptyMap<String, Toggle>()

    override fun read(): Map<String, Toggle> {
        return internalCache
    }

    override fun get(key: String): Toggle? {
        return internalCache[key]
    }

    override fun write(value: Map<String, Toggle>) {
        internalCache = value
    }
}
