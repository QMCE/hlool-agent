package rj.cocacode.services.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import kotlin.system.measureTimeMillis

/**
 * McpTransport interface for MCP transport implementations.
 */
interface McpTransport {
    suspend fun connect()
    suspend fun send(request: McpRequest): String?
    suspend fun disconnect()
}

/**
 * McpLogger for MCP transport logging.
 */
interface McpLogger {
    fun log(message: String)
    fun debug(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

object DefaultMcpLogger : McpLogger {
    override fun log(message: String) = println("[MCP] $message")
    override fun debug(message: String) = println("[MCP DEBUG] $message")
    override fun warn(message: String) = println("[MCP WARN] $message")
    override fun error(message: String, throwable: Throwable?) {
        println("[MCP ERROR] $message")
        throwable?.printStackTrace()
    }
}

/**
 * Represents the state of an MCP connection.
 */
enum class McpConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}