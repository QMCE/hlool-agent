package rj.cocacode.state

import rj.cocacode.types.Conversation
import rj.cocacode.types.Message
import rj.cocacode.utils.Config
import rj.cocacode.utils.generateUuid
import rj.cocacode.config.ApiConfig

data class AppState(
    val sessionId: String = generateUuid(),
    val conversation: Conversation = Conversation(generateUuid()),
    val config: Config = Config(),
    val isConnected: Boolean = false,
    val isThinking: Boolean = false,
    val currentModel: String = ApiConfig.model,
    val permissionMode: PermissionMode = PermissionMode.DEFAULT,
    val mcpServers: Map<String, McpServerState> = emptyMap(),
    val tools: List<ToolState> = emptyList(),
    val tasks: List<TaskState> = emptyList(),
    val theme: String = "auto",
    val debugMode: Boolean = false
)

enum class PermissionMode {
    DEFAULT, BYPASS_PERMISSIONS, PLAN, AUTO, DONT_ASK
}

data class McpServerState(
    val name: String,
    val status: ServerStatus = ServerStatus.DISCONNECTED,
    val tools: List<String> = emptyList(),
    val resources: List<String> = emptyList()
)

enum class ServerStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class ToolState(
    val name: String,
    val enabled: Boolean = true,
    val lastUsed: Long? = null
)

data class TaskState(
    val id: String,
    val type: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f
)

enum class TaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED
}

object AppStateManager {
    private var currentState = AppState()
    private val listeners = mutableListOf<(AppState) -> Unit>()
    
    fun getState(): AppState = currentState
    
    fun updateState(transform: (AppState) -> AppState) {
        currentState = transform(currentState)
        notifyListeners()
    }
    
    fun addListener(listener: (AppState) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (AppState) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        listeners.forEach { it(currentState) }
    }
    
    fun addMessage(message: Message) {
        updateState { state ->
            state.copy(conversation = state.conversation.addMessage(message))
        }
    }
    
    fun setModel(model: String) {
        updateState { it.copy(currentModel = model) }
    }
    
    fun setThinking(thinking: Boolean) {
        updateState { it.copy(isThinking = thinking) }
    }
    
    fun setTheme(theme: String) {
        updateState { it.copy(theme = theme) }
    }
}