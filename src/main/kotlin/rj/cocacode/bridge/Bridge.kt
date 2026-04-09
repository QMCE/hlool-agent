package rj.cocacode.bridge

import rj.cocacode.state.AppStateManager
import rj.cocacode.utils.generateUuid

object Bridge {
    private var isConnected = false
    private var sessionId: String? = null
    private val messageHandlers = mutableMapOf<String, (Map<String, Any>) -> Unit>()
    
    fun connect(serverUrl: String): Boolean {
        return try {
            sessionId = generateUuid()
            isConnected = true
            AppStateManager.updateState { it.copy(isConnected = true) }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun disconnect() {
        isConnected = false
        sessionId = null
        AppStateManager.updateState { it.copy(isConnected = false) }
    }
    
    fun isActive(): Boolean = isConnected
    
    fun getSessionId(): String? = sessionId
    
    fun sendMessage(type: String, payload: Map<String, Any>) {
        if (!isConnected) {
            throw IllegalStateException("Bridge not connected")
        }
    }
    
    fun onMessage(type: String, handler: (Map<String, Any>) -> Unit) {
        messageHandlers[type] = handler
    }
    
    fun processMessage(type: String, payload: Map<String, Any>) {
        messageHandlers[type]?.invoke(payload)
    }
}

object BridgeConfig {
    data class Config(
        val serverUrl: String = "https://claude.ai",
        val protocol: String = "websocket",
        val reconnect: Boolean = true,
        val reconnectInterval: Long = 5000,
        val timeout: Long = 30000
    )
    
    fun load(): Config {
        return Config()
    }
    
    fun save(config: Config) {
    }
}

object BridgeStatus {
    enum class Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    private var currentStatus = Status.DISCONNECTED
    
    fun getStatus(): Status = currentStatus
    
    fun setStatus(status: Status) {
        currentStatus = status
    }
    
    fun isConnected(): Boolean = currentStatus == Status.CONNECTED
}