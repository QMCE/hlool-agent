package rj.cocacode.services.api

import rj.cocacode.types.Message
import kotlinx.coroutines.delay

/**
 * API client for making requests to external services.
 */
object ApiClient {
    suspend fun post(endpoint: String, body: Any): Result<String> {
        // Placeholder: simulate an API call
        delay(100)
        return Result.success("{}")
    }
    
    suspend fun get(endpoint: String): Result<String> {
        delay(100)
        return Result.success("{}")
    }
}

/**
 * Simple Http client wrapper used by OAuthService etc.
 */
object HttpClient {
    val client = io.ktor.client.HttpClient()
}
