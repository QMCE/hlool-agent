package rj.cocacode.hooks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

/**
 * Serializer for arbitrary JSON values.
 * Allows serialization of Any? types from JSON parsing.
 */
object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")
    
    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer only works with JSON")
        
        when (value) {
            null -> jsonEncoder.encodeNull()
            is String -> jsonEncoder.encodeString(value)
            is Number -> jsonEncoder.encodeString(value.toString())
            is Boolean -> jsonEncoder.encodeBoolean(value)
            is List<*> -> jsonEncoder.encodeJsonElement(JsonArray(value.map { anyToJsonElement(it) }))
            is Map<*, *> -> jsonEncoder.encodeJsonElement(JsonObject(value.mapNotNull { (k, v) ->
                (k as? String)?.let { key -> key to anyToJsonElement(v) }
            }.toMap()))
            else -> jsonEncoder.encodeString(value.toString())
        }
    }
    
    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer only works with JSON")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { jsonToAny(it) }
            is JsonObject -> element.toMap().mapValues { jsonToAny(it.value) }
        }
    }
    
    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        is Map<*, *> -> JsonObject(value.mapNotNull { (k, v) ->
            (k as? String)?.let { key -> key to anyToJsonElement(v) }
        }.toMap())
        else -> JsonPrimitive(value.toString())
    }
    
    private fun jsonToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
        }
        is JsonArray -> element.map { jsonToAny(it) }
        is JsonObject -> element.toMap().mapValues { jsonToAny(it.value) }
    }
}

/**
 * JSON utility functions.
 */
object JsonUtils {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    fun <T> parse(jsonString: String, clazz: KClass<T>): T? {
        return try {
            json.decodeFromString(clazz, jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    fun stringify(obj: Any): String {
        return try {
            json.encodeToString(JsonElement.serializer(), anyToJsonElement(obj))
        } catch (e: Exception) {
            "{}"
        }
    }
    
    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        is Map<*, *> -> JsonObject(value.mapNotNull { (k, v) ->
            (k as? String)?.let { key -> key to anyToJsonElement(v) }
        }.toMap())
        is Enum<*> -> JsonPrimitive(value.name)
        else -> JsonPrimitive(value.toString())
    }
    
    fun toMap(jsonString: String): Map<String, Any?> {
        return try {
            json.decodeFromString<Map<String, JsonElement>>(jsonString)
                .mapValues { jsonToAny(it.value) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun jsonToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
        }
        is JsonArray -> element.map { jsonToAny(it) }
        is JsonObject -> element.toMap().mapValues { jsonToAny(it.value) }
    }
}
