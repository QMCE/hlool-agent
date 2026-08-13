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
                // Non-stream defaults; stream() overrides per-request (must not
                // kill long thinking / tool rounds with a 120s wall clock).
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
                "Not logged in. Run /login (access token). Relay sk- is auto-claimed."
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
     * [onChunk] one at a time.
     *
     * Completes successfully only after a terminal SSE marker (`[DONE]`,
     * `message_stop`, or a `finish_reason`/`stop_reason`). A mid-stream TCP drop
     * (read returns null / IOException) is [IncompleteStreamException] — not success.
     */
    suspend fun stream(
        endpoint: String,
        body: JsonElement,
        isAborted: () -> Boolean = { false },
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
                timeout {
                    requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = STREAM_SOCKET_IDLE_MS
                    connectTimeoutMillis = 30_000
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errBody = try {
                        response.bodyAsText().take(500)
                    } catch (_: Exception) {
                        ""
                    }
                    val ex = HttpException(response.status, "Stream ${response.status}: $errBody")
                    // 429 / 5xx are transient for the engine retry loop.
                    return@execute if (response.status.value == 429 || response.status.value >= 500) {
                        Result.failure(IncompleteStreamException(ex.message, ex))
                    } else {
                        Result.failure(ex)
                    }
                }
                val channel = response.bodyAsChannel()
                var finished = false
                while (true) {
                    if (isAborted()) {
                        return@execute Result.failure(rj.cocacode.state.AbortException("Aborted"))
                    }
                    val line = try {
                        channel.readUTF8Line(1_048_576)
                    } catch (e: Exception) {
                        return@execute if (finished) {
                            Result.success(Unit)
                        } else {
                            Result.failure(
                                IncompleteStreamException(
                                    "stream read failed before completion: ${e.message}",
                                    e
                                )
                            )
                        }
                    }
                    if (line == null) {
                        // EOF — only OK if we already saw a terminal event.
                        return@execute if (finished) {
                            Result.success(Unit)
                        } else {
                            Result.failure(
                                IncompleteStreamException("connection closed before stream finished")
                            )
                        }
                    }
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trimStart()
                        when {
                            data == "[DONE]" -> {
                                finished = true
                                break
                            }
                            data.isNotBlank() -> {
                                onChunk(data)
                                if (isTerminalSseData(data)) finished = true
                            }
                        }
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (e is rj.cocacode.state.AbortException) Result.failure(e)
            else if (isRetryableNetwork(e)) {
                Result.failure(IncompleteStreamException(e.message ?: e::class.simpleName ?: "network", e))
            } else {
                Result.failure(e)
            }
        }
    }

    /** Idle gap allowed between SSE lines (thinking can be silent for a while). */
    private const val STREAM_SOCKET_IDLE_MS = 600_000L

    private val terminalFinishReason = Regex(
        "\"(?:finish_reason|stop_reason)\"\\s*:\\s*\"(?:stop|tool_calls|end_turn|length|max_tokens)\""
    )

    private fun isTerminalSseData(data: String): Boolean {
        if (data.contains("\"message_stop\"")) return true
        if (terminalFinishReason.containsMatchIn(data)) return true
        return false
    }

    fun isRetryableNetwork(error: Throwable): Boolean {
        var e: Throwable? = error
        while (e != null) {
            when (e) {
                is IncompleteStreamException -> return true
                is java.io.IOException -> return true
                is java.io.EOFException -> return true
                is java.net.SocketException -> return true
                is java.net.SocketTimeoutException -> return true
                is java.net.ConnectException -> return true
                is java.net.UnknownHostException -> return true
                is java.nio.channels.ClosedChannelException -> return true
                is io.ktor.client.network.sockets.ConnectTimeoutException -> return true
                is io.ktor.client.network.sockets.SocketTimeoutException -> return true
                is io.ktor.client.plugins.HttpRequestTimeoutException -> return true
            }
            val name = e::class.simpleName.orEmpty()
            val msg = e.message.orEmpty().lowercase()
            if (name.contains("Timeout", ignoreCase = true)) return true
            if (msg.contains("connection reset") ||
                msg.contains("broken pipe") ||
                msg.contains("closed") ||
                msg.contains("unreachable") ||
                msg.contains("temporarily unavailable") ||
                msg.contains("stream reset")
            ) {
                return true
            }
            e = e.cause
        }
        return false
    }

    class IncompleteStreamException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

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
    val client = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        expectSuccess = false
    }
}
