package io.getunleash.android.events

import io.getunleash.android.data.ImpressionEvent

interface UnleashEventListener {
    fun onReady() {}
    fun onStateChanged() {}
    fun onImpression(event: ImpressionEvent) {}
}