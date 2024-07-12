package io.getunleash.android.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object Parser {
    val jackson: ObjectMapper =
        jacksonObjectMapper {
            enable(KotlinFeature.NullIsSameAsDefault)
        }.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setDateFormat(
                StdDateFormat().withColonInTimeZone(true)
            ).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
