package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState

interface ToggleCache {
    fun read(): Map<String, Toggle>

    fun get(key: String): Toggle?
    fun write(state: UnleashState)
}

