package rj.cocacode.state

import kotlinx.coroutines.Job
import rj.cocacode.config.ApiConfig

/**
 * AppState represents the global application state.
 */
data class AppState(
    val isConnected: Boolean = false,
    val currentWorkingDirectory: String = System.getProperty("user.dir") ?: "",
    val toolPermissionContext: ToolPermissionContext? = null,
    val isAuthenticated: Boolean = false,
    val sessionId: String? = null,
    val permissionMode: PermissionMode = PermissionMode.DEFAULT,
    val currentModel: String = "claude-sonnet-4-20250514",
    val theme: String = "default",
    val debugMode: Boolean = false
)

/**
 * Permission modes for tool execution control.
 */
enum class PermissionMode {
    DEFAULT,
    BYPASS_PERMISSIONS,
    PLAN,
    AUTO,
    DONT_ASK
}

/**
 * Tool permission context for controlling tool execution permissions.
 */
data class ToolPermissionContext(
    val mode: String = "auto"  // "auto", "ask", "bypass"
)

/**
 * AppStateManager manages the global application state.
 */
object AppStateManager {
    private var _state: AppState = AppState()
    private val listeners = mutableListOf<(AppState) -> Unit>()
    private val messages = mutableListOf<rj.cocacode.types.Message>()
    private var thinking = false
    
    fun getState(): AppState = _state
    
    fun setState(state: AppState) {
        _state = state
    }
    
    fun updateState(transform: (AppState) -> AppState) {
        _state = transform(_state)
        listeners.forEach { it(_state) }
    }
    
    fun addListener(listener: (AppState) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (AppState) -> Unit) {
        listeners.remove(listener)
    }
    
    fun addMessage(message: rj.cocacode.types.Message) {
        messages.add(message)
    }
    
    fun getMessages(): List<rj.cocacode.types.Message> = messages.toList()
    
    fun setThinking(value: Boolean) {
        thinking = value
        _state = _state.copy()
        listeners.forEach { it(_state) }
    }
    
    fun isThinking(): Boolean = thinking
    
    /**
     * Set the current model.
     */
    fun setModel(model: String) {
        _state = _state.copy(currentModel = model)
        listeners.forEach { it(_state) }
    }
    
    /**
     * Set the theme.
     */
    fun setTheme(theme: String) {
        _state = _state.copy(theme = theme)
        listeners.forEach { it(_state) }
    }
    
    /**
     * Reload configuration.
     */
    fun reload() {
        ApiConfig.reload()
    }
}
