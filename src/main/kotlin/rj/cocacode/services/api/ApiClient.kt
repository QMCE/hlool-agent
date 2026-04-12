package rj.cocacode.services.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import rj.cocacode.config.ApiConfig
import rj.cocacode.utils.AuthManager

object HttpClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            AuthManager.getAccessToken()?.let {
                bearerAuth(it)
            }
        }
    }
}

object ApiClient {
    private const val VERSION = "2023-06-01"
    private val baseUrl get() = ApiConfig.baseUrl
    
    suspend fun post(endpoint: String, body: Map<String, Any>): Result<String> {
        return try {
            val response = HttpClient.client.post("$baseUrl$endpoint") {
                header("x-api-key", ApiConfig.apiKey)
                header("anthropic-version", VERSION)
                setBody(body)
            }
            Result.success(response.bodyAsText())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object Bootstrap {
    data class BootstrapResponse(
        val clientData: Map<String, Any>? = null,
        val additionalModelOptions: List<ModelOption>? = null
    )
    
    data class ModelOption(
        val value: String,
        val label: String,
        val description: String
    )
    
    suspend fun fetch(): BootstrapResponse? {
        return try {
            val result = ApiClient.post("/v1/bootstrap", emptyMap())
            result.getOrNull()?.let { parseBootstrapResponse(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseBootstrapResponse(json: String): BootstrapResponse {
        return BootstrapResponse()
    }
}