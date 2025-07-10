package io.github.meesum.telemetry

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Attributes private constructor(private val attributesMap: Map<String, Any>) {

    val json = Json { prettyPrint = true }
    fun getString(key: String): String? = attributesMap[key] as? String
    fun getInt(key: String): Int? = attributesMap[key] as? Int
    fun getLong(key: String): Long? = attributesMap[key] as? Long
    fun getDouble(key: String): Double? = attributesMap[key] as? Double
    fun getBoolean(key: String): Boolean? = attributesMap[key] as? Boolean

    internal fun toOtelAttributes(): io.opentelemetry.api.common.Attributes {
        val builder = io.opentelemetry.api.common.Attributes.builder()
        attributesMap.forEach { (key, value) ->
            when (value) {
                is String -> builder.put(key, value)
                is Int -> builder.put(
                    key,
                    value.toLong()
                ) // OpenTelemetry AttributeKey uses Long for integers
                is Long -> builder.put(key, value)
                is Double -> builder.put(key, value)
                is Boolean -> builder.put(key, value)
            }
        }
        return builder.build()
    }

    internal fun toJsonString(): String {
        val newMap = attributesMap.map { (key, value) ->
            key to value.toString()
        }.toMap()

        return json.encodeToString(
            value = newMap, serializer = MapSerializer(
                keySerializer = String.serializer(),
                valueSerializer = String.serializer()
            )
        )
    }

    class Builder {
        private val attributesMap = mutableMapOf<String, Any>()

        fun put(key: String, value: String): Builder {
            attributesMap[key] = value
            return this
        }

        fun put(key: String, value: Int): Builder {
            attributesMap[key] = value
            return this
        }

        fun put(key: String, value: Long): Builder {
            attributesMap[key] = value
            return this
        }

        fun put(key: String, value: Double): Builder {
            attributesMap[key] = value
            return this
        }

        fun put(key: String, value: Boolean): Builder {
            attributesMap[key] = value
            return this
        }

        fun putAll(attributes: Attributes): Builder {
            attributesMap.putAll(attributes.attributesMap)
            return this
        }

        fun build(): Attributes = Attributes(attributesMap.toMap())
    }

    companion object {
        fun builder(): Builder = Builder()
        fun empty(): Attributes = Attributes(emptyMap())
    }
}
