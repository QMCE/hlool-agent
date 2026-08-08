package rj.cocacode.services.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rj.cocacode.config.ApiConfig
import rj.cocacode.config.ApiType

/**
 * Real API client for external LLM services (Anthropic Messages API and
 * OpenAI-compatible endpoints).
 *
 * - [post] / [get] perform real HTTP requests against [ApiConfig.baseUrl] and
 *   return the raw response body.
 * - [stream] performs an SSE POST and invokes [onChunk] per `data:` line.
 *
 * The auth header is selected by the base URL:
 *  - `api.anthropic.com`   -> `x-api-key` + `anthropic-version`
 *  - anything else         -> `Authorization: Bearer <key>`
 */
object ApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: io.ktor.client.HttpClient by lazy {
        io.ktor.client.HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = ApiConfig.timeout
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = ApiConfig.timeout
            }
            expectSuccess = false
        }
    }

    /** Full URL for an endpoint: baseUrl + endpoint path (or absolute URL). */
    private fun resolve(endpoint: String): String {
        val base = ApiConfig.baseUrl.trimEnd('/')
        val path = endpoint.trimStart('/')
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "$base/$path"
    }

    /** Select auth headers based on the declared [ApiConfig.apiType]. */
    private fun applyAuth(request: HttpRequestBuilder) {
        if (ApiConfig.apiKey.isBlank()) return
        when (ApiConfig.apiType) {
            ApiType.MESSAGES -> {
                request.header("x-api-key", ApiConfig.apiKey)
                request.header("anthropic-version", "2023-06-01")
            }
            ApiType.CHAT -> request.header(HttpHeaders.Authorization, "Bearer ${ApiConfig.apiKey}")
        }
    }

    /** Perform a real POST request with a JSON body. */
    suspend fun post(endpoint: String, body: JsonElement): Result<String> =
        postRaw(endpoint, body.toString())

    private suspend fun postRaw(endpoint: String, requestBody: String): Result<String> {
        if (ApiConfig.apiKey.isBlank()) {
            return Result.failure(IllegalStateException(
                "No API key configured. Set COCA_API_KEY or add apiKey to ~/.cocacode/config.json"
            ))
        }
        return try {
            val response = client.post(resolve(endpoint)) {
                applyAuth(this)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val text = response.bodyAsText()
            if (response.status.isSuccess()) {
                Result.success(text)
            } else {
                Result.failure(HttpException(
                    response.status,
                    "API ${response.status.value} for $endpoint: ${text.take(500)}"
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Perform a real GET request. */
    suspend fun get(endpoint: String): Result<String> {
        return try {
            val response = client.get(resolve(endpoint)) {
                applyAuth(this)
            }
            val text = response.bodyAsText()
            if (response.status.isSuccess()) {
                Result.success(text)
            } else {
                Result.failure(HttpException(response.status, "GET $endpoint -> ${response.status}: ${text.take(500)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform a streaming (SSE) POST request. `data:` payloads are passed to
     * [onChunk] one at a time; `data: [DONE]` terminates the stream.
     */
    suspend fun stream(
        endpoint: String,
        body: JsonElement,
        onChunk: (String) -> Unit
    ): Result<Unit> {
        if (ApiConfig.apiKey.isBlank()) {
            return Result.failure(IllegalStateException("No API key configured."))
        }
        return try {
            client.preparePost(resolve(endpoint)) {
                applyAuth(this)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    return@execute Result.failure(HttpException(response.status, "Stream ${response.status}"))
                }
                val channel = response.bodyAsChannel()
                // SSE is line-oriented: read line-by-line, handing each `data:`
                // payload to onChunk as it arrives.
                while (true) {
                    val line = channel.readUTF8Line(1_048_576) ?: break
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trimStart()
                        if (data != "[DONE]" && data.isNotBlank()) onChunk(data)
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert an arbitrary value (maps, lists, primitives, null) into a
     * [JsonElement]. `@Serializable` objects must be converted by their own
     * serializer before calling [post].
     */
    fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), toJsonElement(v)) }
        }
        is List<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        else -> throw IllegalArgumentException(
            "Cannot serialize value of type ${value::class.simpleName} to JSON"
        )
    }

    /** Simple HTTP exception carrying the status code. */
    data class HttpException(val status: HttpStatusCode, override val message: String) : Exception(message)
}

/**
 * Simple Http client wrapper used by OAuthService etc.
 */
object HttpClient {
    val client = io.ktor.client.HttpClient()
}
