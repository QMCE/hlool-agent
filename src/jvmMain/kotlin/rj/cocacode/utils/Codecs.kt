package rj.cocacode.utils

import com.google.gson.Gson
import java.security.MessageDigest

object JsonUtils {
    private val gson = Gson()
    
    fun parse(json: String): Any? = gson.fromJson(json, Any::class.java)
    
    fun stringify(obj: Any): String = gson.toJson(obj)
    
    fun <T> parseAs(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
    
    fun stringifyPretty(obj: Any): String {
        return gson.toJson(obj)
    }
    
    fun isValidJson(json: String): Boolean {
        return try {
            gson.fromJson(json, Any::class.java)
            true
        } catch (e: Exception) {
            false
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