package rj.cocacode.commands

import kotlinx.coroutines.runBlocking
import rj.cocacode.ui.UI
import rj.cocacode.tools.ToolRegistry

sealed class Command {
    abstract val name: String
    abstract val description: String
    abstract suspend fun execute(args: List<String>)
}

object CommandRegistry {
    private val commands = mutableMapOf<String, Command>()
    
    fun init() {
        register(HelpCommand())
        register(SettingsCommand())
        register(ToolsCommand())
        register(ClearCommand())
        register(ResumeCommand())
        register(ExitCommand())
        register(TeammateCommand())
        register(ModelCommand())
        register(ThemeCommand())
        register(DiffCommand())
        register(CopyCommand())
        register(PermissionsCommand())
    }
    
    fun register(command: Command) {
        commands[command.name] = command
    }
    
    fun unregister(name: String) {
        commands.remove(name)
    }
    
    fun get(name: String): Command? = commands[name]
    
    fun getAll(): List<Command> = commands.values.toList()
    
    fun getNames(): List<String> = commands.keys.toList()
    
    fun execute(name: String, args: List<String> = emptyList()) {
        commands[name]?.let { cmd ->
            kotlinx.coroutines.runBlocking {
                cmd.execute(args)
            }
        }
    }
}

class HelpCommand : Command() {
    override val name = "help"
    override val description = "Show commands and keyboard shortcuts"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Keyboard"))
        ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(40)))
        ui.print("  ${"Shift+Tab".padEnd(16)}Cycle permission mode")
        ui.print("  ${"\\\\ + Enter".padEnd(16)}Continue multiline prompt")
        ui.print("  ${"Tab".padEnd(16)}Complete /commands and @paths")
        ui.print("  ${"Ctrl+C".padEnd(16)}Interrupt at prompt (type /exit to quit)")
        ui.print("  ${"Ctrl+O".padEnd(16)}Expand last collapsed tool result (after turn)")
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Commands"))
        ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(40)))
        CommandRegistry.getAll().sortedBy { it.name }.forEach { cmd ->
            ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/${cmd.name.padEnd(14)}")}${cmd.description}")
        }
        ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(40)))
        ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("!command".padEnd(16))}Run a shell command")
        ui.print("")
    }
}

class SettingsCommand : Command() {
    override val name = "settings"
    override val description = "Manage settings (set/get/list)"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val config = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        
        when (args.firstOrNull()) {
            "list" -> {
                ui.print("")
                ui.print("Current settings:")
                ui.print("")
                ui.print("  model:       ${config.model ?: "(not set)"}")
                ui.print("  apiUrl:      ${config.apiUrl ?: "(not set)"}")
                ui.print("  apiKey:      ${if (config.apiKey.isNullOrEmpty()) "(not set)" else "********"}")
                ui.print("  theme:       ${config.theme}")
                ui.print("  fastMode:    ${config.fastMode}")
                ui.print("")
            }
            "get" -> {
                val key = args.getOrNull(1)
                if (key == null) {
                    ui.printError("Usage: /settings get <key>")
                    return
                }
                val value = when (key) {
                    "model" -> config.model
                    "apiUrl" -> config.apiUrl
                    "apiKey" -> if (config.apiKey.isNullOrEmpty()) null else "********"
                    "theme" -> config.theme
                    "fastMode" -> config.fastMode.toString()
                    else -> null
                }
                if (value != null) {
                    ui.print("$key = $value")
                } else {
                    ui.printError("Unknown setting: $key")
                }
            }
            "set" -> {
                val key = args.getOrNull(1)
                val value = args.getOrNull(2)
                if (key == null || value == null) {
                    ui.printError("Usage: /settings set <key> <value>")
                    return
                }
                val newConfig = when (key) {
                    "model" -> {
                        rj.cocacode.state.AppStateManager.setModel(value)
                        config.copy(model = value)
                    }
                    "apiUrl" -> config.copy(apiUrl = value)
                    "apiKey" -> config.copy(apiKey = value)
                    "theme" -> config.copy(theme = value)
                    "fastMode" -> config.copy(fastMode = value.toBoolean())
                    else -> {
                        ui.printError("Unknown setting: $key")
                        return
                    }
                }
                rj.cocacode.utils.ConfigManager.saveGlobalConfig(newConfig)
                rj.cocacode.config.ApiConfig.reload()
                ui.printSuccess("Setting '$key' updated to '$value'")
            }
            else -> {
                ui.print("")
                ui.print("Settings commands:")
                ui.print("  /settings list           - Show all settings")
                ui.print("  /settings get <key>      - Get a setting value")
                ui.print("  /settings set <key> <value> - Set a setting value")
                ui.print("")
                ui.print("Available keys: model, apiUrl, apiKey, theme, fastMode")
                ui.print("")
            }
        }
    }
}

class ToolsCommand : Command() {
    override val name = "tools"
    override val description = "Show available tools"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        ui.print("")
        ui.print("Available tools:")
        ui.print("")
        ToolRegistry.all().forEach { tool ->
            ui.print("  ${tool.name.padEnd(16)} ${tool.description}")
        }
        ui.print("")
    }
}

class ClearCommand : Command() {
    override val name = "clear"
    override val description = "Clear the screen and start a new session"
    override suspend fun execute(args: List<String>) {
        UI.get().clear()
        val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
        engine.startNewSession()
        UI.get().printInfo("Started a new session")
    }
}

class ResumeCommand : Command() {
    override val name = "resume"
    override val description = "Resume a saved session (arrow keys to pick)"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val sessions = rj.cocacode.utils.SessionStorage.listSessions()
        if (sessions.isEmpty()) {
            ui.printInfo("No saved sessions found")
            return
        }

        // If a session id is given directly, load it without the menu.
        args.firstOrNull()?.let { id ->
            val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
            val data = rj.cocacode.utils.SessionStorage.loadSession(id)
                ?: rj.cocacode.utils.SessionStorage.listSessions()
                    .firstOrNull { it.id.startsWith(id) }
                    ?.let { rj.cocacode.utils.SessionStorage.loadSession(it.id) }
            if (data != null) {
                engine.restoreSession(data)
                val label = engine.sessionTitle ?: data.id.take(8)
                ui.printSuccess("Resumed \"$label\" · ${data.messages.size} messages")
                return
            }
            ui.printWarning("Session '$id' not found — showing picker")
        }

        val terminal = ui.terminal()
        val writer = ui.writer() ?: java.io.PrintWriter(System.out, true)
        if (terminal == null) {
            ui.printError("Interactive session picker unavailable")
            return
        }

        // Filter by title/id query if provided and not a bare id hit.
        val query = args.firstOrNull()
        val filtered = if (query.isNullOrBlank()) {
            sessions
        } else {
            val q = query.lowercase()
            sessions.filter { it.title.lowercase().contains(q) || it.id.lowercase().startsWith(q) }
                .ifEmpty { sessions }
        }

        val picked = rj.cocacode.ui.SessionPicker.pick(terminal, writer, filtered)
        if (picked != null) {
            val data = rj.cocacode.utils.SessionStorage.loadSession(picked.id)
            if (data != null) {
                val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
                engine.restoreSession(data)
                ui.printSuccess("Resumed \"${picked.title}\" · ${data.messages.size} messages")
            }
        } else {
            ui.printInfo("Cancelled")
        }
    }
}

class ExitCommand : Command() {
    override val name = "exit"
    override val description = "Exit the application"
    override suspend fun execute(args: List<String>) {
        kotlin.system.exitProcess(0)
    }
}

class ModelCommand : Command() {
    override val name = "model"
    override val description = "Show or set the model"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val name = args.firstOrNull()
        if (name.isNullOrBlank()) {
            ui.printInfo("Current model: ${rj.cocacode.config.ApiConfig.model}")
            ui.print(rj.cocacode.ui.Ansi.dim("Usage: /model <name>"))
            return
        }
        val config = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        rj.cocacode.utils.ConfigManager.saveGlobalConfig(config.copy(model = name))
        rj.cocacode.config.ApiConfig.reload()
        rj.cocacode.state.AppStateManager.setModel(name)
        ui.printSuccess("Model set to $name")
    }
}

class ThemeCommand : Command() {
    override val name = "theme"
    override val description = "Set theme (default/dark/light)"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val themes = listOf("default", "dark", "light")
        val name = args.firstOrNull()
        if (name.isNullOrBlank()) {
            ui.printInfo("Current theme: ${rj.cocacode.state.AppStateManager.getState().theme}")
            ui.print(rj.cocacode.ui.Ansi.dim("Options: ${themes.joinToString(", ")}"))
            return
        }
        if (name !in themes) {
            ui.printError("Unknown theme. Options: ${themes.joinToString(", ")}")
            return
        }
        val config = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        rj.cocacode.utils.ConfigManager.saveGlobalConfig(config.copy(theme = name))
        rj.cocacode.state.AppStateManager.setTheme(name)
        // Map theme to Ansi.enabled only for "light" as a soft cue — colors stay on.
        ui.printSuccess("Theme set to $name")
    }
}

class DiffCommand : Command() {
    override val name = "diff"
    override val description = "Show uncommitted git diff"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val result = rj.cocacode.utils.Shell.exec(
            "git",
            listOf("diff", "--no-color"),
            rj.cocacode.utils.Shell.ExecOptions(timeout = 15_000)
        )
        val staged = rj.cocacode.utils.Shell.exec(
            "git",
            listOf("diff", "--cached", "--no-color"),
            rj.cocacode.utils.Shell.ExecOptions(timeout = 15_000)
        )
        val text = buildString {
            if (staged.stdout.isNotBlank()) {
                appendLine(rj.cocacode.ui.Ansi.bold("Staged"))
                appendLine(colorDiff(staged.stdout))
            }
            if (result.stdout.isNotBlank()) {
                appendLine(rj.cocacode.ui.Ansi.bold("Unstaged"))
                appendLine(colorDiff(result.stdout))
            }
            if (isEmpty()) append(rj.cocacode.ui.Ansi.dim("Working tree clean (or not a git repo)"))
        }
        ui.print(text.trimEnd())
    }

    private fun colorDiff(raw: String): String =
        raw.lines().joinToString("\n") { line ->
            when {
                line.startsWith("+") && !line.startsWith("+++") -> rj.cocacode.ui.Ansi.brightGreen(line)
                line.startsWith("-") && !line.startsWith("---") -> rj.cocacode.ui.Ansi.brightRed(line)
                line.startsWith("@@") -> rj.cocacode.ui.Ansi.cyan(line)
                else -> rj.cocacode.ui.Ansi.dim(line)
            }
        }
}

class CopyCommand : Command() {
    override val name = "copy"
    override val description = "Copy last assistant reply to clipboard"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val last = rj.cocacode.engine.QueryEngineManager.getEngine().getHistory()
            .lastOrNull { it.type == rj.cocacode.types.MessageType.ASSISTANT }
        if (last == null || last.content.isBlank()) {
            ui.printWarning("No assistant message to copy")
            return
        }
        val ok = copyToClipboard(last.content)
        if (ok) ui.printSuccess("Copied ${last.content.length} chars to clipboard")
        else ui.printError("Clipboard unavailable (tried wl-copy / xclip / pbcopy)")
    }

    private fun copyToClipboard(text: String): Boolean {
        val commands = listOf(
            listOf("wl-copy"),
            listOf("xclip", "-selection", "clipboard"),
            listOf("pbcopy")
        )
        for (cmd in commands) {
            try {
                val p = ProcessBuilder(cmd).start()
                p.outputStream.bufferedWriter().use { it.write(text) }
                if (p.waitFor() == 0) return true
            } catch (_: Exception) { /* try next */ }
        }
        return false
    }
}

class PermissionsCommand : Command() {
    override val name = "permissions"
    override val description = "Show or set permission mode"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val arg = args.firstOrNull()?.lowercase()
        if (arg.isNullOrBlank()) {
            val mode = rj.cocacode.state.AppStateManager.getState().permissionMode
            ui.printInfo("Permission mode: ${mode.name.lowercase()}")
            ui.print(rj.cocacode.ui.Ansi.dim("default | accept_edits | plan | bypass | dont_ask"))
            ui.print(rj.cocacode.ui.Ansi.dim("Or press Shift+Tab to cycle"))
            return
        }
        val mode = when (arg.replace('-', '_')) {
            "default" -> rj.cocacode.state.PermissionMode.DEFAULT
            "accept_edits", "acceptedits", "accept" -> rj.cocacode.state.PermissionMode.ACCEPT_EDITS
            "plan" -> rj.cocacode.state.PermissionMode.PLAN
            "bypass", "bypass_permissions" -> rj.cocacode.state.PermissionMode.BYPASS_PERMISSIONS
            "dont_ask", "dontask" -> rj.cocacode.state.PermissionMode.DONT_ASK
            else -> {
                ui.printError("Unknown mode: $arg")
                return
            }
        }
        rj.cocacode.state.AppStateManager.setPermissionMode(mode)
        ui.printSuccess("Permission mode: ${mode.name.lowercase()}")
    }
}