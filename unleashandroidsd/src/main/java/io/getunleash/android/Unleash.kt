package io.getunleash.android

import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener

interface Unleash {

    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant);

    fun setContext(context: UnleashContext);

    fun getContext(): UnleashContext;

    fun setMetricsStrategy(strategy: DataStrategy)

    fun setFlagFetchStrategy(strategy: DataStrategy)

    fun addUnleashEventListener(listener: UnleashEventListener)
}