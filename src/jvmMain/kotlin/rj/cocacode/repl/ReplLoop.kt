package rj.cocacode.repl

import rj.cocacode.commands.CommandRegistry
import rj.cocacode.engine.QueryEngineManager
import rj.cocacode.state.AppStateManager
import rj.cocacode.ui.UI
import rj.cocacode.utils.ConfigManager
import rj.cocacode.cli.Exit
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ReplLoop {
    private var isRunning = false
    
    fun start() {
        isRunning = true

        val config = ConfigManager.getGlobalConfig()

        UI.get().printBanner()

        while (isRunning) {
            try {
                val input = UI.get().readLine() ?: break
                val trimmed = input.trim()

                if (trimmed.isNotEmpty()) {
                    runBlocking {
                        processInput(trimmed)
                    }
                }

                checkTeammateCompletions()

            } catch (e: org.jline.reader.EndOfFileException) {
                // EOF (e.g. piped stdin) — exit the REPL cleanly.
                break
            } catch (e: org.jline.reader.UserInterruptException) {
                // Ctrl+C at the prompt — show the /exit hint and continue.
                UI.get().printInfo(rj.cocacode.ui.Ansi.dim("Ctrl+C — type /exit to quit, or keep typing"))
                continue
            } catch (e: Exception) {
                UI.get().printError("Error: ${e.message}")
            }
        }
    }

    /** Surface output from teammates that finished their current turn. */
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
                val command = parts[0]
                val args = parts.getOrNull(1)?.split(" ") ?: emptyList()
                
                CommandRegistry.execute(command, args)
            }
            input.startsWith("!") -> {
                val command = input.substring(1)
                val tool = ToolRegistry.get("Bash")
                tool?.execute(mapOf("command" to command))?.let { result ->
                    if (result.isError) {
                        UI.get().printError(result.text)
                    } else {
                        UI.get().print(result.text)
                    }
                }
            }
            else -> {
                val engine = QueryEngineManager.getEngine()
                val ui = UI.get()
                val out = ui.writer() ?: java.io.PrintWriter(System.out, true)
                val statusBar = rj.cocacode.ui.StatusBar(out)
                val renderer = rj.cocacode.ui.StreamRenderer(out, statusBar)

                ui.printUserMessage(input)
                val startTime = System.currentTimeMillis()
                statusBar.show(rj.cocacode.ui.StatusBar.formatStatus("Musing…", 0, 0))

                // Live status updater: ✽ Musing… (elapsed · ↓ tokens).
                val updater = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    while (isActive) {
                        kotlinx.coroutines.delay(200)
                        val elapsed = System.currentTimeMillis() - startTime
                        statusBar.update(rj.cocacode.ui.StatusBar.formatStatus("Musing…", elapsed, renderer.estimatedTokens()))
                    }
                }

                val response = engine.processQueryStreaming(
                    input,
                    onThinking = { renderer.onThinking(it) },
                    onText = { renderer.onText(it) },
                    onToolCall = { name, desc ->
                        renderer.beforeToolCall()
                        statusBar.printOutput(
                            rj.cocacode.ui.Ansi.gray("●") + " " + rj.cocacode.ui.Ansi.bold(name) +
                                if (desc.isNotBlank()) " " + rj.cocacode.ui.Ansi.dim("($desc)") else "" + "\n"
                        )
                    }
                )
                updater.cancel()
                renderer.finish()
                statusBar.hide()
                out.write("\n")
                out.flush()

                when (response) {
                    is rj.cocacode.engine.QueryResponse.Success -> Unit // already streamed
                    is rj.cocacode.engine.QueryResponse.Error -> {
                        ui.printError(response.message)
                    }
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
                UI.get().printInfo("Resumed session (${engine.sessionId.take(8)}…) — ${history.size} prior message(s). Use /clear new to start fresh.")
            } else {
                UI.get().printInfo("No saved session found — starting fresh.")
            }
        }

        val loop = ReplLoop()
        loop.start()
    }
}