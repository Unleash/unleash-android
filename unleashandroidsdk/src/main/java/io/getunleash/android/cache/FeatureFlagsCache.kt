package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.TogglesReceivedListener


interface OldToggleCache {
    fun read(key: String): Map<String, Toggle>
    fun write(key: String, value: Map<String, Toggle>)
}

private const val CACHE_KEY = "toggles"

interface ToggleCache: OldToggleCache, TogglesReceivedListener {

    fun read(): Map<String, Toggle> {
        return read(CACHE_KEY)
    }
    fun write(value: Map<String, Toggle>) {
        write(CACHE_KEY, value)
    }
    fun readOne(toggle: String): Toggle? {
        return read()[toggle]
    }
}