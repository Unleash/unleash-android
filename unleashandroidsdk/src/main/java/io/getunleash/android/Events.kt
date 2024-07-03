package io.getunleash.android

import io.getunleash.android.cache.ToggleStoreListener
import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.FetchTogglesErrorListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

object Events {

    private val toggleStoreListeners: MutableList<ToggleStoreListener> = CopyOnWriteArrayList()
    private val fetchTogglesErrorListeners: MutableList<FetchTogglesErrorListener> = CopyOnWriteArrayList()
    private val readyListeners: MutableList<ReadyListener> = CopyOnWriteArrayList()



    /**
     * For testing
     */
    fun clear() {
        arrayOf(toggleStoreListeners, fetchTogglesErrorListeners, readyListeners).forEach {
            it.clear()
        }
    }

    suspend fun broadcastTogglesFetchFailed(error: Exception) {
        fetchTogglesErrorListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onError(error)
                }
            }
        }
    }
}