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
    val permissionMode: PermissionMode = PermissionMode.DEFAULT
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
    
    /**
     * Set the current model.
     */
    fun setModel(model: String) {
        // Update would happen here
    }
    
    /**
     * Reload configuration.
     */
    fun reload() {
        ApiConfig.reload()
    }
}
