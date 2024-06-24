package io.getunleash.android.polling

/**
 * Listener for receiving flags from the Unleash server.
 *
 * It's mostly intended to be used internally to update the cache and decoupling writing from fetching.
 */
fun interface TogglesUnchangedListener {
    fun onTogglesReceivedWithoutChanges()
}
