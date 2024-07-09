package io.getunleash.android

import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener
import java.io.Closeable

val disabledVariant = Variant("disabled")

interface Unleash: Closeable {
    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant = disabledVariant): Variant

    fun setContext(context: UnleashContext);

    fun getContext(): UnleashContext;

    fun addUnleashEventListener(listener: UnleashEventListener)
}
