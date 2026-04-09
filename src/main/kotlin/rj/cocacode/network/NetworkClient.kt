package rj.cocacode.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object NetworkClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
    
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3)
        }
    }
}

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

class WebSocketClient(private val url: String) {
    private var isConnected = false
    private val messageHandlers = mutableListOf<(String) -> Unit>()
    
    fun connect(): Boolean {
        isConnected = true
        return true
    }
    
    fun disconnect() {
        isConnected = false
    }
    
    fun send(message: String) {
        if (!isConnected) throw IllegalStateException("Not connected")
    }
    
    fun onMessage(handler: (String) -> Unit) {
        messageHandlers.add(handler)
    }
}

class RestClient(private val baseUrl: String) {
    suspend fun get(path: String, headers: Map<String, String> = emptyMap()): NetworkResult<String> {
        return try {
            val response = NetworkClient.httpClient.get("$baseUrl$path") {
                headers.forEach { (key, value) -> header(key, value) }
            }
            NetworkResult.Success(response.bodyAsText())
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        }
    }
    
    suspend fun post(path: String, body: Any, headers: Map<String, String> = emptyMap()): NetworkResult<String> {
        return try {
            val response = NetworkClient.httpClient.post("$baseUrl$path") {
                headers.forEach { (key, value) -> header(key, value) }
                setBody(body)
            }
            NetworkResult.Success(response.bodyAsText())
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        }
    }
    
    suspend fun put(path: String, body: Any, headers: Map<String, String> = emptyMap()): NetworkResult<String> {
        return try {
            val response = NetworkClient.httpClient.put("$baseUrl$path") {
                headers.forEach { (key, value) -> header(key, value) }
                setBody(body)
            }
            NetworkResult.Success(response.bodyAsText())
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        }
    }
    
    suspend fun delete(path: String, headers: Map<String, String> = emptyMap()): NetworkResult<String> {
        return try {
            val response = NetworkClient.httpClient.delete("$baseUrl$path") {
                headers.forEach { (key, value) -> header(key, value) }
            }
            NetworkResult.Success(response.bodyAsText())
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.message ?: "Unknown error")
        }
    }
}