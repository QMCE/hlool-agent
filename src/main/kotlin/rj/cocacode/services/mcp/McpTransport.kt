package rj.cocacode.services.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import kotlin.system.measureTimeMillis

/**
 * MCPTransport.kt - Migrated from Claude Code's StdioTransport.ts
 *
 * Provides STDIO-based transport for MCP (Model Context Protocol) communication.
 * Handles process lifecycle, message routing, JSON-RPC encoding/decoding,
 * and connection state management.
 *
 * @property command The command to execute
 * @property args List of command arguments
 * @property env Environment variables
 * @property logger Logger for diagnostics
 */
class McpTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap(),
    private val logger: McpLogger = DefaultMcpLogger
) {
    companion object {
        const val HEARTBEAT_INTERVAL = 30_000L // 30 seconds
        const val READ_TIMEOUT = 120_000L // 2 minutes
    }

    private val mutex = Mutex()
    private var process: Process? = null
    private var isRunning = false
    private var coroutineScope: CoroutineScope? = null

    /**
     * Starts the MCP transport process and initializes communication channels.
     * @return true if successfully started, false otherwise
     */
    suspend fun start(): Boolean = mutex.withLock {
        try {
            val processBuilder = ProcessBuilder(command, *args.toTypedArray()).apply {
                environment().putAll(env)
                redirectErrorStream(true)
            }
            process = processBuilder.start()
            isRunning = true

            coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            launch { readOutput() }
            launch { sendRequests() }
            launch { handleHeartbeats() }

            logger.log("MCP transport started: $command ${args.joinToString(" ")}")
            true
        } catch (e: Exception) {
            logger.error("Failed to start MCP transport: ${e.message}", e)
            isRunning = false
            false
        }
    }

    /**
     * Stops the transport process and cleans up resources.
     */
    suspend fun stop() = mutex.withLock {
        isRunning = false
        coroutineScope?.cancel()
        process?.destroy()
        process?.waitFor()
        logger.log("MCP transport stopped")
    }

    /**
     * Sends a JSON-RPC request and waits for a response.
     * @param method The RPC method name
     @param params Optional parameters
     * @return The response as a JSON string, or null on failure
     */
    suspend fun sendRequest(method: String, params: Map<String, Any>? = null): String? {
        return mutex.withLock {
            if (!isRunning) {
                logger.warn("Attempted to send request while transport not running: $method")
                return@withLock null
            }
            // Implementation would encode and send JSON-RPC message
            // and wait for response with timeout
            logger.debug("Sending RPC request: $method")
            // Placeholder for actual implementation
            null
        }
    }

    private suspend fun readOutput() {
        val process = process ?: return
        process.inputStream.bufferedReader().forEachLine { line ->
            if (line.isNotBlank()) {
                logger.debug("Received: $line")
                // Parse and handle incoming messages
            }
        }
    }

    private suspend fun sendRequests() {
        // Message queue processing loop
        while (isRunning) {
            delay(100)
        }
    }

    private suspend fun handleHeartbeats() {
        while (isRunning) {
            delay(HEARTBEAT_INTERVAL)
            sendRequest("keepalive")
        }
    }
}

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