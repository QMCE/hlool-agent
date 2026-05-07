package rj.cocacode.telemetry

import rj.cocacode.utils.LogManager

object Telemetry {
    private var isEnabled = true
    private val events = mutableListOf<TelemetryEvent>()
    
    data class TelemetryEvent(
        val name: String,
        val properties: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun enable() { isEnabled = true }
    fun disable() { isEnabled = false }
    
    fun track(eventName: String, properties: Map<String, Any> = emptyMap()) {
        if (!isEnabled) return
        
        val event = TelemetryEvent(eventName, properties)
        events.add(event)
        
        LogManager.logDebug("Telemetry: $eventName")
    }
    
    fun trackSessionStart(sessionId: String) = 
        track("session_start", mapOf("session_id" to sessionId))
    
    fun trackSessionEnd(sessionId: String, durationMs: Long) = 
        track("session_end", mapOf("session_id" to sessionId, "duration_ms" to durationMs))
    
    fun trackQuery(prompt: String, model: String, tokens: Int) =
        track("query", mapOf("prompt_length" to prompt.length, "model" to model, "tokens" to tokens))
    
    fun trackToolUse(toolName: String, durationMs: Long, success: Boolean) =
        track("tool_use", mapOf("tool" to toolName, "duration_ms" to durationMs, "success" to success))
    
    fun trackError(errorType: String, message: String) =
        track("error", mapOf("type" to errorType, "message" to message))
    
    fun getEvents(): List<TelemetryEvent> = events.toList()
    
    fun clear() = events.clear()
    
    fun flush() {
        if (events.isEmpty()) return
        
        LogManager.logDebug("Telemetry flush: ${events.size} events")
        events.clear()
    }
}

object UsageTracker {
    private var totalInputTokens = 0L
    private var totalOutputTokens = 0L
    private var totalCost = 0.0
    private var sessionStartTime = System.currentTimeMillis()
    
    fun recordUsage(inputTokens: Int, outputTokens: Int, cost: Double) {
        totalInputTokens += inputTokens
        totalOutputTokens += outputTokens
        totalCost += cost
    }
    
    fun getStats(): UsageStats = UsageStats(
        inputTokens = totalInputTokens,
        outputTokens = totalOutputTokens,
        totalTokens = totalInputTokens + totalOutputTokens,
        cost = totalCost,
        sessionDurationMs = System.currentTimeMillis() - sessionStartTime
    )
    
    fun reset() {
        totalInputTokens = 0
        totalOutputTokens = 0
        totalCost = 0.0
        sessionStartTime = System.currentTimeMillis()
    }
}

data class UsageStats(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val cost: Double,
    val sessionDurationMs: Long
) {
    fun format(): String = """
Total Tokens: $totalTokens (in: $inputTokens, out: $outputTokens)
Cost: ${"%.4f".format(cost)}
Duration: ${sessionDurationMs / 1000}s
    """.trimIndent()
}

object PerformanceMonitor {
    private val measurements = mutableMapOf<String, MutableList<Long>>()
    
    fun measure(name: String, block: () -> Unit) {
        val start = System.nanoTime()
        try {
            block()
        } finally {
            val duration = (System.nanoTime() - start) / 1_000_000
            measurements.getOrPut(name) { mutableListOf() }.add(duration)
        }
    }
    
    suspend fun measureSuspend(name: String, block: suspend () -> Unit) {
        val start = System.nanoTime()
        try {
            block()
        } finally {
            val duration = (System.nanoTime() - start) / 1_000_000
            measurements.getOrPut(name) { mutableListOf() }.add(duration)
        }
    }
    
    fun getStats(name: String): PerformanceStats? {
        val values = measurements[name] ?: return null
        if (values.isEmpty()) return null
        
        val sorted = values.sorted()
        return PerformanceStats(
            count = values.size,
            min = sorted.first(),
            max = sorted.last(),
            avg = values.sum() / values.size,
            p50 = sorted[sorted.size / 2],
            p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)],
            p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]
        )
    }
    
    fun getAllStats(): Map<String, PerformanceStats> {
        return measurements.mapValues { getStats(it.key)!! }
    }
}

data class PerformanceStats(
    val count: Int,
    val min: Long,
    val max: Long,
    val avg: Long,
    val p50: Long,
    val p95: Long,
    val p99: Long
)