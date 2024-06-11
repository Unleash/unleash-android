package io.getunleash.unleashandroid

import android.content.Context
import io.getunleash.unleashandroid.data.Variant

class DefaultUnleash : Unleash {
    override fun isEnabled(toggleName: String, defaultValue: Boolean): Boolean {
        TODO("Haven't yet done the thing")
    }

    override fun getVariant(toggleName: String, defaultValue: Variant) {
        TODO("Haven't yet done the thing")
    }

    override fun setContext(context: Context) {
        TODO("Not yet implemented")
    }
}