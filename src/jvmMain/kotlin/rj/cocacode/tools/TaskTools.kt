package rj.cocacode.tools

import rj.cocacode.tasks.Task
import rj.cocacode.tasks.TaskManager
import rj.cocacode.tasks.TaskStatus

/**
 * Shared in-memory task manager used by the task tools. Mirrors the reference
 * Claude Code task tools backed by a persistent task store; here backed by the
 * local [TaskManager].
 */
internal object TaskTools {
    val manager = TaskManager()
}

/**
 * TaskCreateTool - Create a new task.
 */
class TaskCreateToolImpl : Tool("TaskCreate", "Create a new task") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val subject = input["subject"] as? String
            ?: return ToolResult(text = "Error: subject required", isError = true)
        val description = input["description"] as? String ?: ""

        val task = TaskTools.manager.createTask(type = "task", description = description)
        return ToolResult(
            text = "Created task ${task.id}: $subject",
            metadata = mapOf("taskId" to task.id, "subject" to subject)
        )
    }
}

/**
 * TaskListTool - List all tasks.
 */
class TaskListToolImpl : Tool("TaskList", "List all tasks") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val tasks = TaskTools.manager.getAllTasks()
        if (tasks.isEmpty()) {
            return ToolResult(text = "No tasks")
        }
        val output = tasks.joinToString("\n") { task ->
            "[${task.status.name}] ${task.id} (${task.type}): ${taskDescription(task)}"
        }
        return ToolResult(
            text = output,
            metadata = mapOf("numTasks" to tasks.size)
        )
    }

    private fun taskDescription(task: Task): String =
        (task as? rj.cocacode.tasks.SimpleTask)?.description ?: ""
}

/**
 * TaskOutputTool - Get the status/progress of a task.
 */
class TaskOutputToolImpl : Tool("TaskOutput", "Get the status and progress of a task") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val taskId = input["task_id"] as? String
            ?: return ToolResult(text = "Error: task_id required", isError = true)

        val task = TaskTools.manager.getTask(taskId)
            ?: return ToolResult(text = "Task not found: $taskId", isError = true)

        val progress = (task.progress * 100).toInt()
        val result = task.result
        val error = task.error
        return ToolResult(
            text = buildString {
                appendLine("Task $taskId")
                appendLine("Status: ${task.status.name}")
                appendLine("Type: ${task.type}")
                append("Progress: $progress%")
                if (result != null) append("\nResult: $result")
                if (error != null) append("\nError: $error")
            },
            metadata = mapOf(
                "taskId" to taskId,
                "status" to task.status.name,
                "progress" to progress
            )
        )
    }
}

/**
 * TaskStopTool - Stop/cancel a task.
 */
class TaskStopToolImpl : Tool("TaskStop", "Stop a running task") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val taskId = input["task_id"] as? String
            ?: return ToolResult(text = "Error: task_id required", isError = true)

        val task = TaskTools.manager.getTask(taskId)
            ?: return ToolResult(text = "Task not found: $taskId", isError = true)

        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.FAILED) {
            return ToolResult(text = "Task $taskId already finished: ${task.status.name}")
        }

        TaskTools.manager.cancelTask(taskId)
        return ToolResult(text = "Task $taskId stopped")
    }
}