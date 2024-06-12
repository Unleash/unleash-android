package io.getunleash.unleashandroid

import io.getunleash.unleashandroid.data.DataStrategy
import io.getunleash.unleashandroid.data.UnleashContext
import io.getunleash.unleashandroid.data.Variant
import io.getunleash.unleashandroid.events.UnleashEventListener

class DefaultUnleash() : Unleash {

    init {

    }

    fun initialize() {

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