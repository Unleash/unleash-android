package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import kotlinx.coroutines.flow.Flow

interface ObservableToggleCache : ToggleCache {
    fun subscribeTo(featuresReceived: Flow<Map<String, Toggle>>)

    fun getUpdatesFlow(): Flow<Unit>
}