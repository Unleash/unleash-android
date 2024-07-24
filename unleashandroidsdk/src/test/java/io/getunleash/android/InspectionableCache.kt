package io.getunleash.android

import io.getunleash.android.cache.ToggleCache
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState

/**
 * This class is used to expose the cache for inspection during tests
 */
class InspectionableCache(
    var toggles: Map<String, Toggle> = emptyMap()
): ToggleCache {

    override fun read(): Map<String, Toggle> {
        return toggles
    }

    override fun get(key: String): Toggle? {
        return toggles[key]
    }

    override fun write(state: UnleashState) {
        toggles = state.toggles
    }
}