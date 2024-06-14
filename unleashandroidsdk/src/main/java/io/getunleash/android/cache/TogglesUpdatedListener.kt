package io.getunleash.android.cache

import io.getunleash.android.data.Toggle

fun interface TogglesUpdatedListener {
    /**
     * This method will be called when a toggles updated event is fired.
     */
    fun onTogglesUpdated(toggles: Map<String, Toggle>)
}