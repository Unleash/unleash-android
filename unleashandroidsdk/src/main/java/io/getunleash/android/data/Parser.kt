package io.getunleash.android.data
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.getunleash.android.metrics.MetricsPayload
import io.getunleash.android.polling.ProxyResponse
import java.lang.reflect.Type
import java.util.Date

object Parser {
    /*val jackson: ObjectMapper =
        jacksonObjectMapper {
            enable(KotlinFeature.NullIsSameAsDefault)
        }.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setDateFormat(
                StdDateFormat().withColonInTimeZone(true)
            ).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)*/



    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // for Kotlin support
        .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe()) // for date format
//        .add(DefaultOnNullAdapterFactory())
        .build()
    val proxyResponseAdapter: JsonAdapter<ProxyResponse> = moshi.adapter(ProxyResponse::class.java)
    val metricsBodyAdapter: JsonAdapter<MetricsPayload> = moshi.adapter(MetricsPayload::class.java)

    // Define an adapter to handle null values as default
    class DefaultOnNullAdapterFactory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            val rawType = Types.getRawType(type)
            if (rawType.isAnnotationPresent(DefaultOnNull::class.java)) {
                val delegate = moshi.nextAdapter<Any>(this, type, annotations)
                return object : JsonAdapter<Any>() {
                    override fun fromJson(reader: JsonReader): Any? {
                        if (reader.peek() == JsonReader.Token.NULL) {
                            reader.nextNull<Unit>()
                            return rawType.getDeclaredConstructor().newInstance()
                        }
                        return delegate.fromJson(reader)
                    }

                    override fun toJson(writer: JsonWriter, value: Any?) {
                        delegate.toJson(writer, value)
                    }
                }
            }
            return null
        }
    }

    // Custom annotation to handle default values
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class DefaultOnNull

}
