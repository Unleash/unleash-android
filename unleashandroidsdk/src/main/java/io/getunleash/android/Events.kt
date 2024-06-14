package io.getunleash.android

import io.getunleash.android.cache.TogglesUpdatedListener
import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.TogglesErroredListener
import io.getunleash.android.polling.TogglesReceivedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Events {

    private val togglesUpdatedListeners: MutableList<TogglesUpdatedListener> = mutableListOf()
    private val errorListeners: MutableList<TogglesErroredListener> = mutableListOf()
    private val togglesReceivedListeners: MutableList<TogglesReceivedListener> = mutableListOf()
    private val readyListeners: MutableList<ReadyListener> = mutableListOf()

    suspend fun broacastTogglesReceived(toggles: Map<String, Toggle>) {
        togglesReceivedListeners.forEach { listener ->
            withContext(Dispatchers.IO) {
                launch {
                    listener.onTogglesReceived(toggles)
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
        arrayOf(togglesUpdatedListeners, errorListeners, togglesReceivedListeners, readyListeners).forEach {
            it.clear()
        }
    }
}