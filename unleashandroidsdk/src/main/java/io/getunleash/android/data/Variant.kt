package io.getunleash.android.data

import com.fasterxml.jackson.annotation.JsonProperty

data class Variant(
    val name: String,
    val enabled: Boolean = true,
    @JsonProperty("feature_enabled") val featureEnabled: Boolean = false,
    val payload: Payload? = null
)