package io.getunleash.android

import io.getunleash.android.cache.ToggleStoreListener
import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.TogglesReceivedListener
import io.getunleash.android.polling.FetchTogglesErrorListener
import io.getunleash.android.polling.TogglesUnchangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

object Events {

    private val toggleStoreListeners: MutableList<ToggleStoreListener> = CopyOnWriteArrayList()
    private val fetchTogglesErrorListeners: MutableList<FetchTogglesErrorListener> = CopyOnWriteArrayList()
    private val togglesReceivedListeners: MutableList<TogglesReceivedListener> = CopyOnWriteArrayList()
    private val togglesUnchangedListeners: MutableList<TogglesUnchangedListener> = CopyOnWriteArrayList()
    private val readyListeners: MutableList<ReadyListener> = CopyOnWriteArrayList()

    suspend fun broadcastTogglesReceived(flags: Map<String, Toggle>) {
        togglesReceivedListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onTogglesReceived(flags)
                }
            }
        }
    }

    fun addTogglesReceivedListener(obj: TogglesReceivedListener) {
        togglesReceivedListeners.add(obj)
    }

    /**
     * For testing
     */
    fun clear() {
        arrayOf(toggleStoreListeners, fetchTogglesErrorListeners, togglesReceivedListeners, readyListeners).forEach {
            it.clear()
        }
    }

    suspend fun broadcastTogglesFetchFailed(error: Exception) {
        fetchTogglesErrorListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onFetchTogglesError(error)
                }
            }
        }
    }

    suspend fun broadcastToggleStoreUpdated(value: Map<String, Toggle>) {
        toggleStoreListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onTogglesStored(value)
                }
            }
        }
    }

    suspend fun broadcastTogglesReceivedButNoChangesDetected() {
        togglesUnchangedListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onTogglesReceivedWithoutChanges()
                }
            }
        }
    }
}