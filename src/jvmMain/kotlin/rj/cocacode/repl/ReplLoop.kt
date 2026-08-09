package rj.cocacode.repl

import rj.cocacode.commands.CommandRegistry
import rj.cocacode.engine.QueryEngineManager
import rj.cocacode.state.AppStateManager
import rj.cocacode.ui.UI
import rj.cocacode.utils.ConfigManager
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.tools.BashToolImpl
import rj.cocacode.tools.ReadToolImpl
import rj.cocacode.tools.EditToolImpl
import rj.cocacode.tools.GrepToolImpl
import rj.cocacode.tools.GlobToolImpl
import rj.cocacode.tools.WriteToolImpl
import rj.cocacode.tools.TaskCreateToolImpl
import rj.cocacode.tools.TaskListToolImpl
import rj.cocacode.tools.TaskOutputToolImpl
import rj.cocacode.tools.TaskStopToolImpl
import rj.cocacode.agents.SubAgentTool
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ReplLoop {
    private var isRunning = false
    private var prefill = ""
    private var eofStreak = 0
    private val transcript = rj.cocacode.ui.TranscriptView()

    fun start() {
        isRunning = true
        ConfigManager.getGlobalConfig()
        UI.get().printBanner()
        UI.get().printInfo("Shift+Tab cycle permission · \\+Enter multiline · Ctrl+O expand last tool")

        while (isRunning) {
            try {
                var input = UI.get().readLine(rj.cocacode.ui.TerminalUI.PROMPT, prefill)
                prefill = ""
                eofStreak = 0
                // Trailing \ continues the prompt (multiline).
                while (input != null && input.endsWith("\\")) {
                    val more = UI.get().readLine(rj.cocacode.ui.Ansi.dim("... "), "")
                    if (more == null) {
                        // Spurious EOF mid-multiline — keep what we have.
                        input = input.dropLast(1)
                        break
                    }
                    input = input.dropLast(1) + "\n" + more
                }
                val trimmed = input?.trim()
                if (trimmed == null) {
                    if (!noteSpuriousEof()) break
                    continue
                }

                if (trimmed.isNotEmpty()) {
                    runBlocking { processInput(trimmed) }
                }
                checkTeammateCompletions()
            } catch (e: org.jline.reader.EndOfFileException) {
                // Raw reader use / terminal glitches can raise a one-off EOF.
                // Require a second Ctrl+D (or second EOF) before quitting.
                if (!noteSpuriousEof()) break
            } catch (e: org.jline.reader.UserInterruptException) {
                eofStreak = 0
                UI.get().printInfo(rj.cocacode.ui.Ansi.dim("Ctrl+C — type /exit to quit, or keep typing"))
                continue
            } catch (e: Exception) {
                eofStreak = 0
                UI.get().printError("Error: ${e.message}")
            }
        }
    }

    /** @return false when the REPL should exit. */
    private fun noteSpuriousEof(): Boolean {
        eofStreak++
        if (eofStreak >= 2) return false
        UI.get().printInfo(rj.cocacode.ui.Ansi.dim("Ctrl+D again to exit (or type /exit)"))
        return true
    }

    private fun checkTeammateCompletions() {
        rj.cocacode.agents.TeammateManager.popCompletedReports().forEach { t ->
            val nameColored = coloredName(t)
            val status = if (t.status == rj.cocacode.agents.Teammate.Status.COMPLETED) {
                rj.cocacode.ui.Ansi.brightGreen("done")
            } else {
                rj.cocacode.ui.Ansi.brightRed("failed")
            }
            UI.get().print("")
            UI.get().print("${rj.cocacode.ui.Ansi.gray("✦")} $nameColored $status")
            t.output.lines().forEach { UI.get().print(rj.cocacode.ui.Ansi.gray("  $it")) }
            UI.get().print("")
        }
    }

    private fun coloredName(t: rj.cocacode.agents.Teammate): String = when (t.color) {
        "red" -> rj.cocacode.ui.Ansi.brightRed(t.name)
        "blue" -> rj.cocacode.ui.Ansi.brightBlue(t.name)
        "green" -> rj.cocacode.ui.Ansi.brightGreen(t.name)
        "yellow" -> rj.cocacode.ui.Ansi.brightYellow(t.name)
        "purple", "pink" -> rj.cocacode.ui.Ansi.brightMagenta(t.name)
        "orange" -> rj.cocacode.ui.Ansi.brightYellow(t.name)
        else -> rj.cocacode.ui.Ansi.brightCyan(t.name)
    }

    private suspend fun processInput(input: String) {
        when {
            input.startsWith("/") -> {
                val parts = input.substring(1).split(" ", limit = 2)
                CommandRegistry.execute(parts[0], parts.getOrNull(1)?.split(" ") ?: emptyList())
            }
            input.startsWith("!") -> {
                val tool = ToolRegistry.get("Bash")
                tool?.execute(mapOf("command" to input.substring(1)))?.let { result ->
                    if (result.isError) UI.get().printError(result.text) else UI.get().print(result.text)
                }
            }
            else -> runAgentTurn(input)
        }
    }

    private suspend fun runAgentTurn(input: String) {
        val engine = QueryEngineManager.getEngine()
        val ui = UI.get()
        val out = ui.writer() ?: java.io.PrintWriter(System.out, true)
        val term = ui.terminal()
        // withPrompt=false: never steal Terminal.reader during the turn (TypeAhead
        // raced LineReader and hard-froze on the second prompt).
        val statusBar = rj.cocacode.ui.StatusBar(
            out,
            terminalWidth = { term?.width?.takeIf { it > 0 } ?: 80 },
            withPrompt = false
        )
        val renderer = rj.cocacode.ui.StreamRenderer(out, statusBar)

        engine.formatToolResult = { name, result, inp ->
            rj.cocacode.ui.tools.ToolResultRenderer.render(name, result, inp)
        }
        engine.onToolResult = { name, display, isError ->
            val collapsed = transcript.formatToolForPrint(name, display, isError)
            statusBar.printOutput(collapsed + "\n")
        }
        engine.permissionAsk = { name, inp ->
            val mode = AppStateManager.getState().permissionMode
            when {
                mode == rj.cocacode.state.PermissionMode.BYPASS_PERMISSIONS -> true
                mode == rj.cocacode.state.PermissionMode.ACCEPT_EDITS && name != "Bash" -> true
                term == null -> true
                else -> {
                    val decision = rj.cocacode.ui.PermissionPrompt.ask(term, out, statusBar, name, inp)
                    when (decision) {
                        rj.cocacode.ui.PermissionPrompt.Decision.ALLOW_ONCE -> true
                        rj.cocacode.ui.PermissionPrompt.Decision.ALLOW_SESSION -> {
                            rj.cocacode.permissions.PermissionGate.rememberAllow(name)
                            true
                        }
                        rj.cocacode.ui.PermissionPrompt.Decision.DENY -> false
                    }
                }
            }
        }

        ui.printUserMessage(input)
        val startTime = System.currentTimeMillis()
        statusBar.show(rj.cocacode.ui.StatusBar.formatStatus("Musing…", 0, 0))

        val updater = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            while (isActive) {
                kotlinx.coroutines.delay(200)
                val elapsed = System.currentTimeMillis() - startTime
                statusBar.setStatus(
                    rj.cocacode.ui.StatusBar.formatStatus("Musing…", elapsed, renderer.estimatedTokens())
                )
            }
        }

        val response = try {
            engine.processQueryStreaming(
                input,
                onThinking = { renderer.onThinking(it) },
                onText = {
                    renderer.onText(it)
                    transcript.addText(it)
                },
                onToolCall = { name, desc ->
                    renderer.beforeToolCall()
                    val descPart =
                        if (desc.isNotBlank()) " " + rj.cocacode.ui.Ansi.dim("($desc)") else ""
                    statusBar.printOutput(
                        rj.cocacode.ui.Ansi.gray("●") + " " +
                            rj.cocacode.ui.Ansi.bold(name) + descPart + "\n"
                    )
                },
                onNotice = { msg ->
                    statusBar.printOutput(rj.cocacode.ui.Ansi.dim("  ⚠ $msg") + "\n")
                }
            )
        } finally {
            updater.cancel()
            updater.join()
            renderer.finish()
            statusBar.hide()
            out.write("\n")
            out.flush()
        }

        when (response) {
            is rj.cocacode.engine.QueryResponse.Success -> Unit
            is rj.cocacode.engine.QueryResponse.Error -> {
                ui.printError(response.message)
                // Make post-tool failures impossible to miss above the prompt.
                if (response.message.contains("After tools") ||
                    response.message.contains("empty response after tools")
                ) {
                    ui.printWarning("Turn stopped after a tool — the model did not continue. Retry or /permissions bypass.")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        UI.shutdown()
    }
}

object Repl {
    fun registerTools() {
        ToolRegistry.register(BashToolImpl())
        ToolRegistry.register(ReadToolImpl())
        ToolRegistry.register(EditToolImpl())
        ToolRegistry.register(GrepToolImpl())
        ToolRegistry.register(SubAgentTool())
        ToolRegistry.register(GlobToolImpl())
        ToolRegistry.register(WriteToolImpl())
        ToolRegistry.register(TaskCreateToolImpl())
        ToolRegistry.register(TaskListToolImpl())
        ToolRegistry.register(TaskOutputToolImpl())
        ToolRegistry.register(TaskStopToolImpl())
        ToolRegistry.register(rj.cocacode.tools.SendMessageTool())
        ToolRegistry.register(rj.cocacode.tools.TeamCreateTool())
    }

    fun start(resume: Boolean = false) {
        UI.init()
        registerTools()
        CommandRegistry.init()
        rj.cocacode.config.ApiConfig.reload()

        if (resume) {
            val engine = QueryEngineManager.getEngine()
            if (engine.restoreLastSession()) {
                val history = engine.getHistory()
                val label = engine.sessionTitle ?: engine.sessionId.take(8)
                UI.get().printInfo("Resumed \"$label\" · ${history.size} messages. /clear for a new session.")
            } else {
                UI.get().printInfo("No saved session found — starting fresh.")
            }
        }

        ReplLoop().start()
    }
}
