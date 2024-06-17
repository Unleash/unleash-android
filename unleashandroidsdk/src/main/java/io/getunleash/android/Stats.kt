package io.getunleash.android

import io.getunleash.android.cache.ToggleStoreListener
import io.getunleash.android.data.Toggle
import io.getunleash.android.polling.FetchTogglesErrorListener
import io.getunleash.android.polling.TogglesUnchangedListener
import java.util.Date

object Stats: ToggleStoreListener, FetchTogglesErrorListener, TogglesUnchangedListener {
    var lastFetchAttempt: Date? = null
    var lastFetchSuccess: Date? = null
    var lastSendMetricsAttempt: Date? = null
    var lastUpdate: Date? = null
    var lastMetricsSent: Date? = null

    var fetchErrorCount: Int = 0
    var sendMetricsErrorCount: Int = 0
    var togglesCount: Int = 0
    // TODO what other information we want to have? OS version?

    override fun onFetchTogglesError(e: Exception) {
        fetchErrorCount++
    }

    override fun onTogglesStored(flags: Map<String, Toggle>) {
        lastUpdate = Date()
        lastFetchSuccess = Date()
        togglesCount = flags.size
    }

    override fun onTogglesReceivedWithoutChanges() {
        lastFetchSuccess = Date()
    }
}