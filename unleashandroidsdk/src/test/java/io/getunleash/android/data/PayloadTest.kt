package io.getunleash.android.data

import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

private const val stringPayloadVariant = "{\n" +
        "        \"name\": \"123\",\n" +
        "        \"payload\": {\n" +
        "          \"type\": \"string\",\n" +
        "          \"value\": \"11\"\n" +
        "        },\n" +
        "        \"enabled\": true\n" +
        "      }"

class PayloadTest {

    @Test
    fun testGetValueAsString() {
        val variant = Parser.jackson.readValue(stringPayloadVariant, Variant::class.java)
        assertThat(variant.payload?.getValueAsString()).isEqualTo("11")
    }

    @Test
    fun testGetValueAsNumber() {
        val variant = Parser.jackson.readValue(stringPayloadVariant, Variant::class.java)
        assertThat(variant.payload?.getValueAsInt()).isEqualTo(11)
    }

    @Test
    fun testGetValueAsBool() {
        val variant = Parser.jackson.readValue(stringPayloadVariant, Variant::class.java)
        assertThat(variant.payload?.getValueAsBoolean()).isFalse()
    }

    @Test
    fun `Able to parse payload value as string`() {
        val response: ProxyResponse = Parser.jackson.readValue(TestResponses.threeToggles)
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["variantToggle"]!!
        assertThat(toggle.variant.payload!!.getValueAsString()).isEqualTo("some-text")
    }

    @Test
    fun `Able to parse payload value as integer`() {
        val response: ProxyResponse = Parser.jackson.readValue(TestResponses.complicatedVariants)
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["variantToggle"]!!
        assertThat(toggle.variant.payload!!.getValueAsInt()).isEqualTo(54)
    }

    @Test
    fun `Able to parse payload value as boolean`() {
        val response: ProxyResponse = Parser.jackson.readValue(TestResponses.complicatedVariants)
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["booleanVariant"]!!
        assertThat(toggle.variant.payload!!.getValueAsBoolean()).isTrue
    }

    @Test
    fun `Able to parse payload value as double`() {
        val response: ProxyResponse = Parser.jackson.readValue(TestResponses.complicatedVariants)
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["doubleVariant"]!!
        assertThat(toggle.variant.payload!!.getValueAsDouble()).isEqualTo(42.0)
    }
}