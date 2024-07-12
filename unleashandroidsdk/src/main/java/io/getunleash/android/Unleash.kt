package io.getunleash.android

import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener
import java.io.Closeable

val disabledVariant = Variant("disabled")

interface Unleash: Closeable {
    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant = disabledVariant): Variant


    /**
     * Set context and trigger a fetch of the latest toggles immediately and block until the fetch is complete or failed.
     */
    fun setContext(context: UnleashContext)

    /**
     * Set context and trigger a fetch of the latest toggles asynchronously
     */
    fun setContextWithTimeout(context: UnleashContext, timeout: Long = 5000)

    /**
     * Set context and trigger a fetch of the latest toggles asynchronously
     */
    fun setContextAsync(context: UnleashContext)

    fun getContext(): UnleashContext

    fun addUnleashEventListener(listener: UnleashEventListener)
}
