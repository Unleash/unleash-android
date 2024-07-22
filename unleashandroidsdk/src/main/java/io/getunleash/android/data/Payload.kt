package io.getunleash.android.data

import com.fasterxml.jackson.databind.JsonNode

data class Payload (val type: String, val value: JsonNode) {
    /**
     * Helper to extract value as String from JsonNode
     */
    fun getValueAsString(): String? {
        return value.textValue()
    }
    fun getValueAsInt() = value.asInt()
    fun getValueAsDouble() = value.asDouble()
    fun getValueAsBoolean() = value.asBoolean()
}