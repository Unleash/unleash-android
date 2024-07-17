package io.getunleash.android.events

import io.getunleash.android.data.ImpressionEvent

interface UnleashListener
interface UnleashReadyListener: UnleashListener {
    fun onReady()
}

interface UnleashStateListener: UnleashListener {
    fun onStateChanged()
}

interface UnleashImpressionEventListener: UnleashListener {
    fun onImpression(event: ImpressionEvent)
}
