package io.getunleash.android.events

import io.getunleash.android.data.ImpressionEvent

interface UnleashImpressionEventListener: UnleashListener {
    fun onImpression(event: ImpressionEvent)
}