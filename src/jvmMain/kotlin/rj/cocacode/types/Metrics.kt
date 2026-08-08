package rj.cocacode.types

/**
 * Metrics snapshot for a query/model call.
 *
 * Reconstructed from the pre-migration `types/Metrics.kt`. `rj.cocacode.types.Metrics`
 * is imported by the query engine for token/latency accounting.
 */
data class Metrics(
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val tokensUsed: Int = 0,
    val latencyMs: Long = 0,
    val model: String? = null,
    val stopReason: String? = null
)

/**
 * Structured API error surfaced to the query loop.
 */
data class ApiError(
    val type: String,
    val message: String,
    val code: Int? = null
)

/**
 * Raised when the primary model call fails and a fallback model should be tried.
 */
class FallbackTriggeredError(message: String, val originalError: Throwable? = null) : Exception(message)