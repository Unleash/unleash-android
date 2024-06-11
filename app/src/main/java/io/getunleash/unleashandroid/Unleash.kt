package io.getunleash.unleashandroid

import io.getunleash.unleashandroid.data.Context
import io.getunleash.unleashandroid.data.Variant

interface Unleash {

    fun isEnabled(toggleName: String, defaultValue: Boolean = false): Boolean

    fun getVariant(toggleName: String, defaultValue: Variant);

    fun setContext(context: Context);
}