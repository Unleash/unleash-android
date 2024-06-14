package io.getunleash.android.cache

import io.getunleash.android.data.Toggle

fun interface ToggleStoreListener {
    /**
     * This method will be called after the feature toggle store (usually a cache) is updated.
     */
    fun onTogglesStored(flags: Map<String, Toggle>)
}
