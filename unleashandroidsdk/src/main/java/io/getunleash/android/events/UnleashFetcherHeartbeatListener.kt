package io.getunleash.android.events

interface UnleashFetcherHeartbeatListener: UnleashListener {
    fun onError(event: HeartbeatEvent)
    fun togglesChecked()
    fun togglesUpdated()
}