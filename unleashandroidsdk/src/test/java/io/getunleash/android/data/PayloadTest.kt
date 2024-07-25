package io.getunleash.android.data

import com.squareup.moshi.JsonAdapter
import io.getunleash.android.data.Parser.moshi
import io.getunleash.android.data.Parser.proxyResponseAdapter
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

    val variantsAdapter: JsonAdapter<Variant> = moshi.adapter(Variant::class.java)
    @Test
    fun testGetValueAsString() {
        val variant = variantsAdapter.fromJson(stringPayloadVariant)!!
        assertThat(variant.payload?.getValueAsString()).isEqualTo("11")
    }

    @Test
    fun testGetValueAsNumber() {
        val variant = variantsAdapter.fromJson(stringPayloadVariant)!!
        assertThat(variant.payload?.getValueAsInt()).isEqualTo(11)
    }

    @Test
    fun testGetValueAsBool() {
        val variant = variantsAdapter.fromJson(stringPayloadVariant)!!
        assertThat(variant.payload?.getValueAsBoolean()).isFalse()
    }

    @Test
    fun `Able to parse payload value as string`() {
        val response = proxyResponseAdapter.fromJson(TestResponses.threeToggles)!!
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["variantToggle"]!!
        assertThat(toggle.variant.payload!!.getValueAsString()).isEqualTo("some-text")
    }

    @Test
    fun `Able to parse payload value as integer`() {
        val response = proxyResponseAdapter.fromJson(TestResponses.complicatedVariants)!!
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["variantToggle"]!!
        assertThat(toggle.variant.payload!!.getValueAsInt()).isEqualTo(54)
    }

    @Test
    fun `Able to parse payload value as boolean`() {
        val response = proxyResponseAdapter.fromJson(TestResponses.complicatedVariants)!!
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["booleanVariant"]!!
        assertThat(toggle.variant.payload!!.getValueAsBoolean()).isTrue
    }

    @Test
    fun `Able to parse payload value as double`() {
        val response = proxyResponseAdapter.fromJson(TestResponses.complicatedVariants)!!
        val map = response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        val toggle = map["doubleVariant"]!!
        assertThat(toggle.variant.payload!!.getValueAsDouble()).isEqualTo(42.0)
    }
}