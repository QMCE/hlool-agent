package rj.cocacode.types

import kotlinx.serialization.Serializable

@Serializable
data class Metrics(
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val tokensUsed: Int = 0,
    val latencyMs: Long = 0,
    val model: String? = null,
    val stopReason: String? = null
)

@Serializable
data class ApiError(
    val type: String,
    val message: String,
    val code: Int? = null
)

class FallbackTriggeredError(message: String, val originalError: Throwable? = null) : Exception(message)