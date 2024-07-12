package io.getunleash.android.cache

import io.getunleash.android.data.UnleashState
import kotlinx.coroutines.flow.Flow

interface ObservableToggleCache : ToggleCache {
    fun subscribeTo(featuresReceived: Flow<UnleashState>)

    fun getUpdatesFlow(): Flow<UnleashState>
}