package io.getunleash.android.polling

import io.getunleash.android.data.Toggle

fun interface TogglesReceivedListener {
    fun onTogglesReceived(toggles: Map<String, Toggle>)
}