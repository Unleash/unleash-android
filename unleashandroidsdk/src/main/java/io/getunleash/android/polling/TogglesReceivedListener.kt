package io.getunleash.android.polling

import io.getunleash.android.data.Toggle

/**
 * Listener for receiving flags from the Unleash server.
 *
 * It's mostly intended to be used internally to update the cache and decoupling writing from fetching.
 */
fun interface TogglesReceivedListener {
    fun onTogglesReceived(toggles: Map<String, Toggle>)
}
