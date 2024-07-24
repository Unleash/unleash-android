package io.getunleash.android.events

import io.getunleash.android.polling.Status


/**
 * Event published to client indicating status the last operation performed by a background task
 */
data class HeartbeatEvent(val status: Status, val message: String? = null)