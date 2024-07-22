package io.getunleash.android.data

data class Payload (val type: String, val value: String) {

    fun getValueAsString() = value
    fun getValueAsInt() = value.toInt()
    fun getValueAsDouble() = value.toDouble()
    fun getValueAsBoolean() = value.toBoolean()
}