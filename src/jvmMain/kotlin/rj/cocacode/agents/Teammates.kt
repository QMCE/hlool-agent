package rj.cocacode.agents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.utils.generateUuid
import rj.cocacode.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A running teammate: an autonomous agent with its own mailbox.
 *
 * Mirrors the reference in-process teammate: the agent runs a task, goes idle,
 * and polls its mailbox for follow-up messages (from the user or the main
 * agent via SendMessage). Each incoming message triggers a new run with the
 * accumulated conversation as context.
 */
class Teammate(
    val id: String,
    val name: String,
    val prompt: String,
    /** Display color name: red/blue/green/yellow/purple/orange/pink/cyan. */
    val color: String
) {
    enum class Status { RUNNING, IDLE, COMPLETED, FAILED, KILLED }

    @Volatile
    var status: Status = Status.RUNNING
        private set

    @Volatile
    var output: String = ""
        private set

    var error: String? = null
        private set

    val turnCount: Int get() = history.size

    private val mailbox = ConcurrentLinkedQueue<String>()
    private val aborted = AtomicBoolean(false)
    private val history = mutableListOf<Message>()
    private val lock = Any()

    /** Queue a message for this teammate; it will run again to process it. */
    fun postMessage(message: String) {
        mailbox.add(message)
    }

    /** The next queued message, or null if the mailbox is empty. */
    fun pollMessage(): String? = mailbox.poll()

    val queuedCount: Int get() = mailbox.size

    fun requestKill() {
        aborted.set(true)
    }

    val isAborted: Boolean get() = aborted.get()

    fun setStatus(s: Status) {
        synchronized(lock) { status = s }
    }

    fun setOutput(o: String) {
        synchronized(lock) { output = o }
    }

    fun setError(e: String) {
        synchronized(lock) { error = e }
    }

    fun appendToHistory(messages: List<Message>) {
        synchronized(lock) { history.addAll(messages) }
    }

    fun historySnapshot(): List<Message> = synchronized(lock) { history.toList() }
}

/**
 * Manages all active teammates. In-process: each teammate runs in a background
 * coroutine driving [SubAgentEngine].
 */
object TeammateManager {

    private val teammates = ConcurrentHashMap<String, Teammate>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Global teammates-mode switch. When enabled, subagents spawned with a
     * `name` become persistent teammates (addressable via SendMessage) instead
     * of one-shot agents. Cocacode defaults to teammates mode.
     */
    @Volatile
    var teammatesEnabled: Boolean = true

    /** Round-robin display colors (matches reference agentColorManager). */
    private val COLORS = listOf("red", "blue", "green", "yellow", "purple", "orange", "pink", "cyan")
    private var colorIndex = 0

    fun spawn(name: String, prompt: String, agentType: String = "general-purpose"): Result<Teammate> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Teammate name is required"))
        }
        if (teammates.containsKey(trimmed)) {
            return Result.failure(IllegalArgumentException("Teammate '$trimmed' already exists"))
        }

        val teammate = Teammate(
            id = "t_" + generateUuid().take(8),
            name = trimmed,
            prompt = prompt,
            color = nextColor()
        )
        teammates[trimmed] = teammate

        Logger.info("Spawning teammate '$trimmed' (${teammate.id})")
        scope.launch {
            try {
                runTeammateLoop(teammate, agentType)
            } catch (e: Exception) {
                Logger.error("Teammate '$trimmed' crashed", e)
                teammate.setError(e.message ?: "Unknown error")
                teammate.setStatus(Teammate.Status.FAILED)
            }
        }
        return Result.success(teammate)
    }

    fun get(name: String): Teammate? = teammates[name]

    fun list(): List<Teammate> = teammates.values.sortedBy { it.name }

    /** Track which teammate turns have already been reported to the UI. */
    private val reportedTurns = ConcurrentHashMap<String, Int>()

    /**
     * Returns teammates that completed a turn since the last call, so the REPL
     * can surface their output. Each completed turn is reported once.
     */
    fun popCompletedReports(): List<Teammate> {
        val result = mutableListOf<Teammate>()
        for (t in teammates.values) {
            val turns = t.turnCount
            val reported = reportedTurns.getOrDefault(t.name, 0)
            if ((t.status == Teammate.Status.COMPLETED || t.status == Teammate.Status.FAILED) && turns > reported) {
                reportedTurns[t.name] = turns
                result.add(t)
            }
        }
        return result
    }

    fun send(name: String, message: String): Boolean {
        val teammate = teammates[name] ?: return false
        teammate.postMessage(message)
        return true
    }

    fun requestShutdown(name: String): Boolean {
        val teammate = teammates[name] ?: return false
        // Politely finish current work, then stop polling the mailbox.
        teammate.requestKill()
        return true
    }

    fun kill(name: String): Boolean {
        val teammate = teammates[name] ?: return false
        teammate.requestKill()
        teammate.setStatus(Teammate.Status.KILLED)
        teammates.remove(name)
        return true
    }

    fun activeCount(): Int = teammates.size

    fun clearAll() {
        teammates.values.forEach { it.requestKill() }
        teammates.clear()
    }

    // --- internals ---

    private fun nextColor(): String {
        val color = COLORS[colorIndex % COLORS.size]
        colorIndex++
        return color
    }

    private suspend fun runTeammateLoop(teammate: Teammate, agentType: String) {
        // Initial task.
        executeTask(teammate, teammate.prompt, agentType)

        // Idle loop: poll the mailbox for follow-up messages.
        while (!teammate.isAborted) {
            val message = teammate.pollMessage()
            if (message != null) {
                teammate.setStatus(Teammate.Status.RUNNING)
                executeTask(teammate, message, agentType)
            } else {
                if (teammate.status == Teammate.Status.RUNNING) {
                    teammate.setStatus(Teammate.Status.IDLE)
                }
                delay(500)
            }
        }
        teammate.setStatus(Teammate.Status.KILLED)
    }

    private suspend fun executeTask(teammate: Teammate, task: String, agentType: String) {
        teammate.setStatus(Teammate.Status.RUNNING)
        val definition = try {
            AgentLoader().getAgent(agentType) ?: AgentLoader().getAgent("general-purpose")
        } catch (e: Exception) {
            null
        } ?: AgentDefinition(
            agentType = "general-purpose",
            whenToUse = "General-purpose teammate",
            systemPrompt = "You are a helpful teammate agent. Be concise and thorough."
        )

        val context = SubAgentContext(
            definition = definition,
            task = task,
            parentMessages = teammate.historySnapshot().takeLast(40),
            workingDirectory = System.getProperty("user.dir") ?: ".",
            isChildOfFork = true
        )

        val result = SubAgentEngineManager.getEngine().execute(context)

        teammate.appendToHistory(
            listOf(
                Message(id = generateUuid(), type = MessageType.USER, content = task),
                Message(
                    id = generateUuid(),
                    type = MessageType.ASSISTANT,
                    content = result.output,
                    isError = result.isError
                )
            )
        )

        teammate.setOutput(result.output)
        if (result.isError) {
            teammate.setError("Turn failed")
            teammate.setStatus(Teammate.Status.FAILED)
        } else {
            teammate.setStatus(Teammate.Status.COMPLETED)
        }
    }
}
