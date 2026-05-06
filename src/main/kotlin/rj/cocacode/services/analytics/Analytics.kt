package rj.cocacode.services.analytics

import rj.cocacode.utils.LogManager
import java.util.concurrent.ConcurrentLinkedQueue

object Analytics {
    private val eventQueue = ConcurrentLinkedQueue<AnalyticsEvent>()
    private var isEnabled = true
    private var sink: AnalyticsSink? = null
    
    fun initialize() {
        sink = DefaultAnalyticsSink()
    }
    
    fun enable() { isEnabled = true }
    fun disable() { isEnabled = false }
    
    fun logEvent(name: String, properties: Map<String, Any> = emptyMap()) {
        if (!isEnabled) return
        
        val event = AnalyticsEvent(
            name = name,
            properties = properties,
            timestamp = System.currentTimeMillis(),
            sessionId = rj.cocacode.state.AppStateManager.getState().sessionId ?: "unknown"
        )
        
        eventQueue.offer(event)
        processQueue()
    }
    
    private fun processQueue() {
        while (true) {
            val event = eventQueue.poll() ?: break
            sink?.send(event)
        }
    }
    
    fun flush() {
        processQueue()
    }
}

data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any>,
    val timestamp: Long,
    val sessionId: String
)

interface AnalyticsSink {
    fun send(event: AnalyticsEvent)
}

class DefaultAnalyticsSink : AnalyticsSink {
    override fun send(event: AnalyticsEvent) {
        LogManager.logDebug("Analytics: ${event.name}")
    }
}

object AnalyticsEvents {
    const val SESSION_START = "session_start"
    const val SESSION_END = "session_end"
    const val QUERY_START = "query_start"
    const val QUERY_END = "query_end"
    const val TOOL_USE = "tool_use"
    const val TOOL_RESULT = "tool_result"
    const val ERROR = "error"
    const val PERMISSION_ASK = "permission_ask"
    const val PERMISSION_ALLOW = "permission_allow"
    const val PERMISSION_DENY = "permission_deny"
}