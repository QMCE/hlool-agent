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
        register(LoginCommand())
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

class LoginCommand : Command() {
    override val name = "login"
    override val description = "One-key SHUAI API login (sk-… unlocks full web models)"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val auth = rj.cocacode.services.oauth.ShuaiApiAuth
        val base = rj.cocacode.constants.Product.API_BASE_URL
        val first = args.firstOrNull()?.trim().orEmpty()

        when {
            first.equals("status", ignoreCase = true) -> {
                val status = auth.fetchStatus(base)
                if (status == null) {
                    ui.printError("Could not reach $base/api/status")
                    return
                }
                ui.print("")
                ui.print(rj.cocacode.ui.Ansi.bold(status.systemName))
                ui.print("  server:   ${status.serverAddress}")
                ui.print("  github:   ${status.githubOAuth}")
                ui.print("  linuxdo:  ${status.linuxdoOAuth}")
                ui.print("  password: ${status.passwordLogin}")
                ui.print("  passkey:  ${status.passkeyLogin}")
                ui.print("")
                val configured = rj.cocacode.config.ApiConfig.isConfigured
                ui.print("  local key: ${if (configured) "set" else "not set"}")
                ui.print("  baseUrl:   ${rj.cocacode.config.ApiConfig.baseUrl}")
                ui.print("")
            }
            first.equals("oauth", ignoreCase = true) || first.equals("browser", ignoreCase = true) -> {
                browserLogin(ui, auth, base)
            }
            first.equals("password", ignoreCase = true) || first.equals("user", ignoreCase = true) -> {
                passwordLogin(ui, auth, base, args.drop(1))
            }
            first.startsWith("sk-") || (first.length > 20 && !first.contains(' ')) -> {
                saveKey(ui, auth, base, first)
            }
            first.isEmpty() -> {
                ui.print("")
                ui.print(rj.cocacode.ui.Ansi.bold("Hlool Agent · SHUAI API login"))
                ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(44)))
                ui.print("One console API key unlocks the same models/chat surface as the web app.")
                ui.print("")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login sk-…")}         Paste a key from the console")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login oauth")}        Open browser (GitHub / LinuxDo / Passkey)")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login password")}     Username + password → auto-create sk key")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login status")}       Show gateway auth options")
                ui.print("")
                ui.print(rj.cocacode.ui.Ansi.dim("Token page: ${rj.cocacode.constants.Product.TOKEN_CONSOLE_URL}"))
                ui.print("")
                // Default interactive path: open browser, then prompt for key.
                browserLogin(ui, auth, base)
            }
            else -> {
                ui.printError("Unknown /login argument. Try /login, /login sk-…, /login oauth, or /login password")
            }
        }
    }

    private suspend fun browserLogin(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        base: String
    ) {
        val status = auth.fetchStatus(base)
        if (status != null) {
            val methods = buildList {
                if (status.githubOAuth) add("GitHub")
                if (status.linuxdoOAuth) add("LinuxDo")
                if (status.passkeyLogin) add("Passkey")
                if (status.passwordLogin) add("password")
            }
            ui.printInfo("Gateway auth: ${methods.joinToString(" · ").ifEmpty { "console login" }}")
        }
        val opened = auth.openBrowserAuth(base)
        if (opened) {
            ui.printInfo("Opened browser → login, then copy an API token from the console.")
        } else {
            ui.printWarning("Could not open browser. Visit:")
            ui.print("  ${rj.cocacode.constants.Product.LOGIN_URL}")
            ui.print("  ${rj.cocacode.constants.Product.TOKEN_CONSOLE_URL}")
        }
        ui.print(rj.cocacode.ui.Ansi.dim("Paste the sk- key below (or run /login sk-… later)."))
        val key = ui.readLine("API key ▸ ", "")?.trim().orEmpty()
        if (key.isEmpty()) {
            ui.printInfo("Cancelled — run /login sk-… when you have a key")
            return
        }
        saveKey(ui, auth, base, key)
    }

    private suspend fun passwordLogin(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        base: String,
        rest: List<String>
    ) {
        val username = rest.getOrNull(0)
            ?: ui.readLine("Username ▸ ", "")?.trim().orEmpty()
        if (username.isEmpty()) {
            ui.printError("Username required")
            return
        }
        val password = rest.getOrNull(1)
            ?: ui.readLine("Password ▸ ", "")?.trim().orEmpty()
        if (password.isEmpty()) {
            ui.printError("Password required")
            return
        }
        ui.printInfo("Signing in…")
        val login = auth.passwordLogin(username, password, base)
        val session = login.getOrElse {
            ui.printError(it.message ?: "Login failed")
            return
        }
        ui.printInfo("Creating Hlool Agent relay token…")
        val token = auth.createRelayToken(session.accessToken, base).getOrElse {
            ui.printError(it.message ?: "Token create failed")
            ui.print(rj.cocacode.ui.Ansi.dim("Fallback: open ${rj.cocacode.constants.Product.TOKEN_CONSOLE_URL} and /login sk-…"))
            return
        }
        saveKey(ui, auth, base, token.key)
    }

    private suspend fun saveKey(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        base: String,
        rawKey: String
    ) {
        val key = auth.normalizeKey(rawKey) ?: run {
            ui.printError("Empty API key")
            return
        }
        ui.printInfo("Validating against $base/v1/models …")
        val err = auth.validateApiKey(key, base)
        if (err != null) {
            ui.printError(err)
            return
        }
        val prev = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        val next = prev.copy(
            apiKey = key,
            baseUrl = base,
            apiUrl = base,
            apiType = "chat",
            model = prev.model ?: rj.cocacode.constants.Product.DEFAULT_MODEL
        )
        rj.cocacode.utils.ConfigManager.saveGlobalConfig(next)
        rj.cocacode.config.ApiConfig.reload()
        if (next.model != null) {
            rj.cocacode.state.AppStateManager.setModel(next.model)
        }
        ui.printSuccess("Logged in — one key ready for full SHUAI web models")
        ui.print(rj.cocacode.ui.Ansi.dim("Saved to ~/.hlool-agent/config.json · model ${rj.cocacode.config.ApiConfig.model}"))
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
                ui.print("  baseUrl:     ${config.baseUrl ?: config.apiUrl ?: "(not set)"}")
                ui.print("  apiType:     ${config.apiType ?: "(auto)"}")
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
                    "apiUrl", "baseUrl" -> config.baseUrl ?: config.apiUrl
                    "apiType" -> config.apiType
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
                    "apiUrl", "baseUrl" -> config.copy(apiUrl = value, baseUrl = value)
                    "apiType" -> config.copy(apiType = value)
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
                ui.print("Available keys: model, baseUrl, apiType, apiKey, theme, fastMode")
                ui.print(rj.cocacode.ui.Ansi.dim("Tip: /login configures SHUAI API with one key"))
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