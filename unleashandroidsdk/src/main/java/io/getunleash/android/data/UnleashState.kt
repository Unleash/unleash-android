package io.getunleash.android.data

data class UnleashState (
    val context: UnleashContext,
    val toggles: Map<String, Toggle>
)