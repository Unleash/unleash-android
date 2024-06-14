package io.getunleash.android

import io.getunleash.android.cache.InMemoryToggleCache
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.Variant
import io.getunleash.android.events.UnleashEventListener

class DefaultUnleash() : Unleash {

    init {

    }

    fun initialize() {
        val cache = InMemoryToggleCache()
        Events.addTogglesReceivedListener(cache)

    }

    override fun isEnabled(toggleName: String, defaultValue: Boolean): Boolean {
        TODO("Haven't yet done the thing")
    }

    override fun getVariant(toggleName: String, defaultValue: Variant) {
        TODO("Haven't yet done the thing")
    }

    override fun setContext(context: UnleashContext) {
        TODO("Not yet implemented")
        //eventBus.rehydrateData(context)
        // important, this should not force an HTTP call but put an event to do
        // that at some point in the nearish future so we don't get DDoS'd
    }

    override fun getContext(): UnleashContext {
        TODO("Not yet implemented")
    }

    override fun setMetricsStrategy(strategy: DataStrategy) {
        TODO("Not yet implemented")
    }

    override fun setFlagFetchStrategy(strategy: DataStrategy) {
        TODO("Not yet implemented")
    }

    override fun addUnleashEventListener(listener: UnleashEventListener) {
        TODO("Not yet implemented")
    }
}
