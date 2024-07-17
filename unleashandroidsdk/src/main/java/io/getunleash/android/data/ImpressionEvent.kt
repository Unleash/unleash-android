package io.getunleash.android.data

import java.util.UUID

data class ImpressionEvent(
    val featureName: String,
    val enabled: Boolean,
    val context: UnleashContext,
    val variant: String? = null,
    val eventId: String =  UUID.randomUUID().toString(),
)
