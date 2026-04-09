package rj.cocacode.tasks

sealed class TaskState {
    abstract val status: String
    abstract val type: String
}

data class LocalShellTaskState(
    override val status: String,
    override val type: String,
    val command: String,
    val description: String,
    val isBackgrounded: Boolean = false,
    val notified: Boolean = false
) : TaskState()

data class LocalAgentTaskState(
    override val status: String,
    override val type: String,
    val description: String,
    val isBackgrounded: Boolean = false
) : TaskState()

data class RemoteAgentTaskState(
    override val status: String,
    override val type: String,
    val description: String,
    val isBackgrounded: Boolean = false,
    val isUltraplan: Boolean = false,
    val ultraplanPhase: String? = null
) : TaskState()

data class InProcessTeammateTaskState(
    override val status: String,
    override val type: String,
    val description: String,
    val teamName: String,
    val isBackgrounded: Boolean = false
) : TaskState()

data class LocalWorkflowTaskState(
    override val status: String,
    override val type: String,
    val description: String,
    val isBackgrounded: Boolean = false
) : TaskState()

data class MonitorMcpTaskState(
    override val status: String,
    override val type: String,
    val description: String,
    val isBackgrounded: Boolean = false
) : TaskState()

data class DreamTaskState(
    override val status: String,
    override val type: String,
    val description: String
) : TaskState()

sealed class BackgroundTaskState : TaskState()

fun isBackgroundTask(task: TaskState): Boolean {
    if (task.status != "running" && task.status != "pending") {
        return false
    }
    return when (task) {
        is TaskState -> {
            val isBackgrounded = when (task) {
                is LocalShellTaskState -> task.isBackgrounded
                is LocalAgentTaskState -> task.isBackgrounded
                is RemoteAgentTaskState -> task.isBackgrounded
                is InProcessTeammateTaskState -> task.isBackgrounded
                is LocalWorkflowTaskState -> task.isBackgrounded
                is MonitorMcpTaskState -> task.isBackgrounded
                else -> true
            }
            isBackgrounded != false
        }
        else -> true
    }
}