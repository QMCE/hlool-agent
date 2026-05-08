package rj.cocacode.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

object JsonUtils {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonString: String): Any? {
        return try {
            val element = json.parseToJsonElement(jsonString)
            jsonElementToAny(element)
        } catch (e: Exception) {
            null
        }
    }

    fun stringify(obj: Any): String = json.encodeToString(JsonElement.serializer(), anyToJsonElement(obj))

    fun stringifyPretty(obj: Any): String {
        val element = anyToJsonElement(obj)
        return json.encodeToString(JsonElement.serializer(), element)
    }

    fun isValidJson(jsonString: String): Boolean {
        return try {
            json.parseToJsonElement(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                element.contentOrNull ?: element.longOrNull ?: element.toString()
            }
            is JsonObject -> {
                element.jsonObject.mapValues { jsonElementToAny(it.value) }
            }
            else -> element.toString()
        }
    }

    private fun anyToJsonElement(obj: Any): JsonElement {
        return when (obj) {
            null -> JsonNull
            is Map<*, *> -> JsonObject(obj.entries.associate { it.key.toString() to anyToJsonElement(it.value!!) })
            is List<*> -> kotlinx.serialization.json.JsonArray(obj.map { anyToJsonElement(it!!) })
            is Number -> when (obj) {
                is Int -> JsonPrimitive(obj)
                is Long -> JsonPrimitive(obj)
                is Float -> JsonPrimitive(obj)
                is Double -> JsonPrimitive(obj)
                else -> JsonPrimitive(obj.toDouble())
            }
            is Boolean -> JsonPrimitive(obj)
            is String -> JsonPrimitive(obj)
            else -> JsonPrimitive(obj.toString())
        }
    }
}

object CryptoUtils {
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun sha256(input: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val digest = sha.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun base64Encode(input: String): String {
        return java.util.Base64.getEncoder().encodeToString(input.toByteArray())
    }
    
    fun base64Decode(encoded: String): String {
        return String(java.util.Base64.getDecoder().decode(encoded))
    }
    
    fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}