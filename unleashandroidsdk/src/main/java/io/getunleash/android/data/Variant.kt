package io.getunleash.android.data

import com.squareup.moshi.Json

data class Variant(
    val name: String,
    val enabled: Boolean = false,
    @Json(name = "feature_enabled") val featureEnabled: Boolean = false,
    val payload: Payload? = null
)
