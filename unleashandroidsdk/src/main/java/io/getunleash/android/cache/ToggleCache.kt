package io.getunleash.android.cache

import io.getunleash.android.data.Toggle

interface ToggleCache {
    fun read(): Map<String, Toggle>

    fun get(key: String): Toggle?
    fun write(value: Map<String, Toggle>)
}

