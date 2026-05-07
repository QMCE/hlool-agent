package rj.cocacode.tasks

import rj.cocacode.utils.generateUuid

sealed class Task {
    abstract val id: String
    abstract val type: String
    abstract val status: TaskStatus
    abstract val createdAt: Long
    
    var progress: Float = 0f
    var result: Any? = null
    var error: String? = null
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SimpleTask(
    override val id: String = generateUuid(),
    override val type: String,
    override val status: TaskStatus = TaskStatus.PENDING,
    override val createdAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val metadata: Map<String, Any> = emptyMap()
) : Task()

class TaskManager {
    private val tasks = mutableMapOf<String, Task>()
    private val listeners = mutableListOf<(Task) -> Unit>()
    
    fun createTask(type: String, description: String = ""): SimpleTask {
        val task = SimpleTask(type = type, description = description)
        tasks[task.id] = task
        notifyListeners(task)
        return task
    }
    
    fun startTask(taskId: String) {
        val task = tasks[taskId] as? SimpleTask ?: return
        tasks[taskId] = task.copy(status = TaskStatus.RUNNING)
        notifyListeners(tasks[taskId]!!)
    }
    
    fun updateProgress(taskId: String, progress: Float) {
        val task = tasks[taskId] ?: return
        task.progress = progress.coerceIn(0f, 1f)
        notifyListeners(task)
    }
    
    fun completeTask(taskId: String, result: Any? = null) {
        val task = tasks[taskId] as? SimpleTask ?: return
        tasks[taskId] = task.copy(status = TaskStatus.COMPLETED)
        task.result = result
        notifyListeners(task)
    }
    
    fun failTask(taskId: String, error: String) {
        val task = tasks[taskId] as? SimpleTask ?: return
        tasks[taskId] = task.copy(status = TaskStatus.FAILED)
        task.error = error
        notifyListeners(task)
    }
    
    fun cancelTask(taskId: String) {
        val task = tasks[taskId] as? SimpleTask ?: return
        tasks[taskId] = task.copy(status = TaskStatus.CANCELLED)
        notifyListeners(task)
    }
    
    fun getTask(taskId: String): Task? = tasks[taskId]
    
    fun getAllTasks(): List<Task> = tasks.values.toList()
    
    fun getTasksByStatus(status: TaskStatus): List<Task> =
        tasks.values.filter { it.status == status }
    
    fun getRunningTasks(): List<Task> = getTasksByStatus(TaskStatus.RUNNING)
    
    fun clearCompletedTasks() {
        tasks.values.removeAll { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
    }
    
    fun addListener(listener: (Task) -> Unit) {
        listeners.add(listener)
    }
    
    private fun notifyListeners(task: Task) {
        listeners.forEach { it(task) }
    }
}

object TaskScheduler {
    private var isRunning = false
    private val pendingTasks = mutableListOf<Task>()
    private val scheduledTasks = mutableMapOf<Long, MutableList<Task>>()
    
    fun schedule(task: Task, delayMs: Long) {
        val executeAt = System.currentTimeMillis() + delayMs
        scheduledTasks.getOrPut(executeAt) { mutableListOf() }.add(task)
    }
    
    fun enqueue(task: Task) {
        pendingTasks.add(task)
    }
    
    fun start() {
        isRunning = true
    }
    
    fun stop() {
        isRunning = false
    }
    
    fun processScheduled() {
        val now = System.currentTimeMillis()
        val due = scheduledTasks.filter { it.key <= now }
        
        for ((time, tasks) in due) {
            pendingTasks.addAll(tasks)
            scheduledTasks.remove(time)
        }
    }
    
    fun getNextScheduledTime(): Long? {
        return scheduledTasks.keys.minOrNull()
    }
}