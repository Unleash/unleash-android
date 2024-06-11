package io.getunleash.unleashandroid

import io.getunleash.unleashandroid.data.DataStrategy
import io.getunleash.unleashandroid.data.UnleashContext
import io.getunleash.unleashandroid.data.Variant
import io.getunleash.unleashandroid.events.UnleashEventListener

interface Unleash {

    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant);

    fun setContext(context: UnleashContext);

    fun getContext(): UnleashContext;

    fun setMetricsStrategy(strategy: DataStrategy)

    fun setFlagFetchStrategy(strategy: DataStrategy)

    fun addUnleashEventListener(listener: UnleashEventListener)
}