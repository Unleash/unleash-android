package io.getunleash.android.cache

import io.getunleash.android.data.Toggle


interface ToggleCache {
    fun read(key: String): Map<String, Toggle>
    fun write(key: String, value: Map<String, Toggle>)
}