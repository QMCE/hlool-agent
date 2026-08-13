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
        register(ConsoleCommand())
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
    override val description = "Login with access token (auto-claims relay sk-; no sk paste)"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val auth = rj.cocacode.services.oauth.ShuaiApiAuth
        val base = rj.cocacode.constants.Product.API_BASE_URL
        val first = args.firstOrNull()?.trim().orEmpty()

        when {
            first.equals("status", ignoreCase = true) -> showStatus(ui, auth, base)
            first.equals("logout", ignoreCase = true) -> logout(ui)
            first.equals("refresh", ignoreCase = true) -> refreshRelay(ui, auth)
            first.equals("password", ignoreCase = true) || first.equals("user", ignoreCase = true) ->
                passwordLogin(ui, auth, base, args.drop(1))
            first.equals("access", ignoreCase = true) || first.equals("atok", ignoreCase = true) -> {
                val token = args.getOrNull(1)?.trim().orEmpty().ifEmpty {
                    ui.readLine("Access token ▸ ", "")?.trim().orEmpty()
                }
                if (token.isEmpty()) {
                    ui.printError("Access token required")
                    return
                }
                if (token.startsWith("sk-")) {
                    ui.printError("Do not paste sk- keys. Use the console Access Token (personal settings).")
                    return
                }
                activateAccess(ui, auth, base, token, args.getOrNull(2)?.toIntOrNull())
            }
            first.equals("oauth", ignoreCase = true) || first.equals("browser", ignoreCase = true) ->
                browserAccessLogin(ui, auth, base)
            first.startsWith("sk-") -> {
                ui.printError("Manual sk- login is disabled. Use /login password or /login access <token>.")
                ui.print(rj.cocacode.ui.Ansi.dim("Relay sk- is auto-claimed and persisted from your access token."))
            }
            first.isEmpty() -> {
                ui.print("")
                ui.print(rj.cocacode.ui.Ansi.bold("Hlool Agent · access-token login"))
                ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(48)))
                ui.print("Credential = Access Token. Relay sk- is auto-claimed — never pasted.")
                ui.print("")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login")}                 Password login (recommended)")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login access <token>")}  Paste console Access Token")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login oauth")}          Browser → personal settings → access token")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login refresh")}        Re-claim relay sk- from access token")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login status")}         Show session / gateway")
                ui.print("  ${rj.cocacode.ui.Ansi.brightCyan("/login logout")}         Clear saved credentials")
                ui.print("")
                ui.print(rj.cocacode.ui.Ansi.dim("Then manage the account with /console (balance, tokens, logs, …)"))
                ui.print("")
                passwordLogin(ui, auth, base, emptyList())
            }
            else -> {
                // Treat bare token as access token (not sk-)
                if (first.length > 16 && !first.contains(' ')) {
                    activateAccess(ui, auth, base, first, null)
                } else {
                    ui.printError("Unknown /login arg. Try /login, /login access <token>, or /login password")
                }
            }
        }
    }

    private suspend fun showStatus(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        base: String
    ) {
        val status = auth.fetchStatus(base)
        val cfg = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        ui.print("")
        if (status != null) {
            ui.print(rj.cocacode.ui.Ansi.bold(status.systemName))
            ui.print("  server:   ${status.serverAddress}")
            ui.print("  github:   ${status.githubOAuth} · linuxdo: ${status.linuxdoOAuth}")
            ui.print("  password: ${status.passwordLogin} · passkey: ${status.passkeyLogin}")
        }
        ui.print("  access:   ${if (cfg.accessToken.isNullOrBlank()) "not set" else "set"}")
        ui.print("  user:     ${cfg.username ?: "-"} (#${cfg.userId ?: "-"})")
        ui.print("  relay sk: ${if (cfg.apiKey.isNullOrBlank()) "not claimed" else "auto (#${cfg.relayTokenId ?: "?"})"}")
        ui.print("  baseUrl:  ${cfg.baseUrl ?: base}")
        ui.print("")
    }

    private fun logout(ui: rj.cocacode.ui.TerminalUI) {
        val prev = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        rj.cocacode.utils.ConfigManager.saveGlobalConfig(
            prev.copy(accessToken = null, apiKey = null, userId = null, username = null, relayTokenId = null)
        )
        rj.cocacode.config.ApiConfig.reload()
        ui.printSuccess("Logged out — credentials cleared")
    }

    private suspend fun refreshRelay(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        ui.printInfo("Re-claiming relay token…")
        val relay = auth.ensureRelayToken(forceRefresh = true).getOrElse {
            ui.printError(it.message ?: "Failed")
            return
        }
        val cfg = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        rj.cocacode.utils.ConfigManager.saveGlobalConfig(cfg.copy(apiKey = relay.key, relayTokenId = relay.id))
        rj.cocacode.config.ApiConfig.reload()
        ui.printSuccess("Relay token ready (#${relay.id} · ${relay.name})")
    }

    private suspend fun browserAccessLogin(
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
            }
            ui.printInfo("Browser auth: ${methods.joinToString(" · ").ifEmpty { "console login" }}")
        }
        if (!auth.openPersonalSettings(base)) {
            ui.printWarning("Open: $base/login then $base/console/personal")
        } else {
            ui.printInfo("Opened personal settings — generate/copy Access Token (not sk-).")
        }
        val token = ui.readLine("Access token ▸ ", "")?.trim().orEmpty()
        if (token.isEmpty()) {
            ui.printInfo("Cancelled")
            return
        }
        if (token.startsWith("sk-")) {
            ui.printError("That looks like an sk- key. Paste the Access Token from personal settings.")
            return
        }
        activateAccess(ui, auth, base, token, null)
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
            ui.printError("Username required (or use /login access <token>)")
            return
        }
        val password = rest.getOrNull(1)
            ?: ui.readLine("Password ▸ ", "")?.trim().orEmpty()
        if (password.isEmpty()) {
            ui.printError("Password required")
            return
        }
        ui.printInfo("Signing in…")
        val session = auth.passwordLogin(username, password, base).getOrElse {
            ui.printError(it.message ?: "Login failed")
            return
        }
        ui.printInfo("Persisting access token + auto-claiming relay sk-…")
        finishActivate(ui, auth, session, base)
    }

    private suspend fun activateAccess(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        base: String,
        accessToken: String,
        userIdHint: Int?
    ) {
        ui.printInfo("Validating access token…")
        val session = auth.fetchSelf(accessToken, userIdHint, base).getOrElse {
            ui.printError(it.message ?: "Invalid access token")
            return
        }
        ui.printInfo("Auto-claiming relay sk-…")
        finishActivate(ui, auth, session, base)
    }

    private suspend fun finishActivate(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        session: rj.cocacode.services.oauth.ShuaiApiAuth.Session,
        base: String
    ) {
        val relay = auth.activateSession(session, base).getOrElse {
            ui.printError(it.message ?: "Failed to claim relay token")
            return
        }
        session.username?.let { /* already saved */ }
        rj.cocacode.state.AppStateManager.setModel(
            rj.cocacode.utils.ConfigManager.getGlobalConfig().model
                ?: rj.cocacode.constants.Product.DEFAULT_MODEL
        )
        ui.printSuccess("Logged in as ${session.username ?: "#${session.userId}"}")
        ui.print(rj.cocacode.ui.Ansi.dim(
            "access token saved · relay #${relay.id} auto-claimed · balance ${auth.formatQuota(session.quota)}"
        ))
        ui.print(rj.cocacode.ui.Ansi.dim("Manage account: /console · models: /console models"))
    }
}

class ConsoleCommand : Command() {
    override val name = "console"
    override val description = "SHUAI web console: balance, tokens, models, logs, topup…"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val auth = rj.cocacode.services.oauth.ShuaiApiAuth
        val action = args.firstOrNull()?.lowercase() ?: "help"
        when (action) {
            "help", "?" -> help(ui)
            "balance", "self", "me", "account" -> balance(ui, auth)
            "models" -> models(ui, auth)
            "groups" -> groups(ui, auth)
            "tokens" -> tokens(ui, auth)
            "create-token" -> createToken(ui, auth, args.drop(1))
            "enable-token" -> setToken(ui, auth, args.getOrNull(1), true)
            "disable-token" -> setToken(ui, auth, args.getOrNull(1), false)
            "delete-token" -> deleteToken(ui, auth, args.getOrNull(1))
            "switch-group" -> switchGroup(ui, auth, args.getOrNull(1), args.getOrNull(2))
            "logs" -> logs(ui, auth, args.drop(1))
            "topup", "topup-info" -> topup(ui, auth)
            "topup-history" -> topupHistory(ui, auth)
            "notice" -> notice(ui, auth)
            "pricing" -> pricing(ui, auth)
            "aff" -> aff(ui, auth)
            "claim", "claim-relay" -> {
                ui.printInfo("Claiming relay sk- from access token…")
                val relay = auth.ensureRelayToken(forceRefresh = true).getOrElse {
                    ui.printError(it.message ?: "claim failed"); return
                }
                val cfg = rj.cocacode.utils.ConfigManager.getGlobalConfig()
                rj.cocacode.utils.ConfigManager.saveGlobalConfig(cfg.copy(apiKey = relay.key, relayTokenId = relay.id))
                rj.cocacode.config.ApiConfig.reload()
                ui.printSuccess("Relay #${relay.id} ready (key never printed)")
            }
            else -> {
                ui.printError("Unknown /console action: $action")
                help(ui)
            }
        }
    }

    private fun help(ui: rj.cocacode.ui.TerminalUI) {
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("SHUAI console (web parity)"))
        ui.print(rj.cocacode.ui.Ansi.gray("─".repeat(48)))
        ui.print("  /console balance              Account + quota")
        ui.print("  /console models               Available models")
        ui.print("  /console groups               User groups")
        ui.print("  /console tokens               List API tokens (keys masked)")
        ui.print("  /console create-token <name> [--group=g] [--quota=1.0]")
        ui.print("  /console switch-group <id> <group>")
        ui.print("  /console enable-token <id> | disable-token <id> | delete-token <id>")
        ui.print("  /console logs [model]")
        ui.print("  /console topup | topup-history | notice | pricing | aff")
        ui.print("  /console claim                Re-claim Hlool Agent relay sk-")
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.dim("Requires /login (access token). sk- keys are never pasted or printed."))
        ui.print("")
    }

    private suspend fun balance(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val cfg = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        val access = cfg.accessToken ?: run { ui.printError("Not logged in"); return }
        val self = auth.fetchSelf(access, cfg.userId).getOrElse {
            ui.printError(it.message ?: "failed"); return
        }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Account"))
        ui.print("  user:     ${self.username} (#${self.userId})")
        ui.print("  display:  ${self.displayName ?: "-"}")
        ui.print("  group:    ${self.group ?: "-"}")
        ui.print("  quota:    ${auth.formatQuota(self.quota)}")
        ui.print("  used:     ${auth.formatQuota(self.usedQuota)}")
        ui.print("  requests: ${self.requestCount}")
        ui.print("  relay:    #${cfg.relayTokenId ?: "-"} ${if (cfg.apiKey.isNullOrBlank()) "(missing)" else "(ready)"}")
        ui.print("")
    }

    private suspend fun models(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val list = auth.listModels().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Models (${list.size})"))
        list.sorted().chunked(2).forEach { row ->
            ui.print("  " + row.joinToString("  ") { it.padEnd(36).take(36) })
        }
        ui.print("")
    }

    private suspend fun groups(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val data = auth.listGroups().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Groups"))
        data.entrySet().forEach { (name, el) ->
            val desc = if (el.isJsonObject) {
                val o = el.asJsonObject
                val ratio = o.get("ratio")?.toString() ?: "?"
                val d = o.get("desc")?.asString ?: ""
                "ratio=$ratio  $d"
            } else el.toString()
            ui.print("  ${name.padEnd(16)} $desc")
        }
        ui.print("")
    }

    private suspend fun tokens(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val rows = auth.listTokens().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Tokens (${rows.size})"))
        ui.print(
            "  ${"ID".padEnd(6)}${"Name".padEnd(18)}${"Status".padEnd(8)}${"Group".padEnd(14)}${"Key".padEnd(22)}Quota"
        )
        rows.forEach { t ->
            val st = when (t.status) { 1 -> "on"; 2 -> "off"; 3 -> "exp"; else -> t.status.toString() }
            val q = if (t.unlimitedQuota) "∞" else auth.formatQuota(t.remainQuota)
            ui.print(
                "  ${t.id.toString().padEnd(6)}${t.name.take(16).padEnd(18)}${st.padEnd(8)}${t.group.take(12).padEnd(14)}${t.keyMasked.take(20).padEnd(22)}$q"
            )
        }
        ui.print("")
    }

    private suspend fun createToken(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        rest: List<String>
    ) {
        val name = rest.firstOrNull { !it.startsWith("--") }
        if (name.isNullOrBlank()) {
            ui.printError("Usage: /console create-token <name> [--group=g] [--quota=1.0]")
            return
        }
        val group = rest.firstOrNull { it.startsWith("--group=") }?.substringAfter("=")
        val quota = rest.firstOrNull { it.startsWith("--quota=") }?.substringAfter("=")?.toDoubleOrNull()
        val unlimited = quota == null
        val id = auth.createToken(name, group, unlimited, quota).getOrElse {
            ui.printError(it.message ?: "failed"); return
        }
        ui.printSuccess("Created token #$id ($name). Key not printed — use /console claim if this is the agent relay.")
    }

    private suspend fun setToken(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        idStr: String?,
        enabled: Boolean
    ) {
        val id = idStr?.toIntOrNull() ?: run {
            ui.printError("Usage: /console ${if (enabled) "enable" else "disable"}-token <id>")
            return
        }
        auth.setTokenStatus(id, enabled).getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.printSuccess("Token #$id ${if (enabled) "enabled" else "disabled"}")
    }

    private suspend fun deleteToken(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        idStr: String?
    ) {
        val id = idStr?.toIntOrNull() ?: run { ui.printError("Usage: /console delete-token <id>"); return }
        auth.deleteToken(id).getOrElse { ui.printError(it.message ?: "failed"); return }
        val cfg = rj.cocacode.utils.ConfigManager.getGlobalConfig()
        if (cfg.relayTokenId == id) {
            rj.cocacode.utils.ConfigManager.saveGlobalConfig(cfg.copy(apiKey = null, relayTokenId = null))
            rj.cocacode.config.ApiConfig.reload()
        }
        ui.printSuccess("Deleted token #$id")
    }

    private suspend fun switchGroup(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        idStr: String?,
        group: String?
    ) {
        val id = idStr?.toIntOrNull()
        if (id == null || group.isNullOrBlank()) {
            ui.printError("Usage: /console switch-group <token_id> <group>")
            return
        }
        auth.updateTokenGroup(id, group).getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.printSuccess("Token #$id → group $group")
    }

    private suspend fun logs(
        ui: rj.cocacode.ui.TerminalUI,
        auth: rj.cocacode.services.oauth.ShuaiApiAuth,
        rest: List<String>
    ) {
        val model = rest.firstOrNull()
        val rows = auth.listLogs(modelName = model).getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Logs (${rows.size})"))
        rows.forEach { l ->
            ui.print(
                "  #${l.id}  ${l.modelName.padEnd(22)}  ${l.tokenName.padEnd(14)}  " +
                    "in=${l.promptTokens} out=${l.completionTokens}  ${auth.formatQuota(l.quota)}"
            )
        }
        ui.print("")
    }

    private suspend fun topup(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val data = auth.topupInfo().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Top-up info"))
        ui.print("  $data")
        ui.print(rj.cocacode.ui.Ansi.dim("Pay in browser: ${rj.cocacode.constants.Product.CONSOLE_URL}/console/topup"))
        ui.print("")
    }

    private suspend fun topupHistory(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val data = auth.topupHistory().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Top-up history"))
        ui.print("  $data")
        ui.print("")
    }

    private suspend fun notice(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val text = auth.notice().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Notice"))
        text.lines().forEach { ui.print("  $it") }
        ui.print("")
    }

    private suspend fun pricing(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val root = auth.pricingSummary().getOrElse { ui.printError(it.message ?: "failed"); return }
        val groups = root.getAsJsonArray("auto_groups")
        val data = root.getAsJsonArray("data")
        ui.print("")
        ui.print(rj.cocacode.ui.Ansi.bold("Pricing"))
        if (groups != null) ui.print("  auto_groups: ${groups.take(8).joinToString { it.asString }}…")
        ui.print("  models listed: ${data?.size() ?: 0}")
        ui.print(rj.cocacode.ui.Ansi.dim("Full table: ${rj.cocacode.constants.Product.CONSOLE_URL}/pricing"))
        ui.print("")
    }

    private suspend fun aff(ui: rj.cocacode.ui.TerminalUI, auth: rj.cocacode.services.oauth.ShuaiApiAuth) {
        val code = auth.affCode().getOrElse { ui.printError(it.message ?: "failed"); return }
        ui.printSuccess("Affiliate code: $code")
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
                ui.print("  model:         ${config.model ?: "(not set)"}")
                ui.print("  baseUrl:       ${config.baseUrl ?: config.apiUrl ?: "(not set)"}")
                ui.print("  apiType:       ${config.apiType ?: "(auto)"}")
                ui.print("  accessToken:   ${if (config.accessToken.isNullOrEmpty()) "(not set)" else "********"}")
                ui.print("  userId:        ${config.userId ?: "(not set)"}")
                ui.print("  username:      ${config.username ?: "(not set)"}")
                ui.print("  relay sk:      ${if (config.apiKey.isNullOrEmpty()) "(not claimed)" else "auto"}")
                ui.print("  theme:         ${config.theme}")
                ui.print("  fastMode:      ${config.fastMode}")
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
                    "accessToken" -> if (config.accessToken.isNullOrEmpty()) null else "********"
                    "apiKey" -> if (config.apiKey.isNullOrEmpty()) null else "(auto-claimed)"
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
                if (key == "apiKey") {
                    ui.printError("Manual apiKey/sk- is disabled. Use /login (access token); relay sk- is auto-claimed.")
                    return
                }
                val newConfig = when (key) {
                    "model" -> {
                        rj.cocacode.state.AppStateManager.setModel(value)
                        config.copy(model = value)
                    }
                    "apiUrl", "baseUrl" -> config.copy(apiUrl = value, baseUrl = value)
                    "apiType" -> config.copy(apiType = value)
                    "accessToken" -> config.copy(accessToken = value)
                    "theme" -> config.copy(theme = value)
                    "fastMode" -> config.copy(fastMode = value.toBoolean())
                    else -> {
                        ui.printError("Unknown setting: $key")
                        return
                    }
                }
                rj.cocacode.utils.ConfigManager.saveGlobalConfig(newConfig)
                rj.cocacode.config.ApiConfig.reload()
                if (key == "accessToken") {
                    ui.printInfo("Access token saved — claiming relay sk-…")
                    val auth = rj.cocacode.services.oauth.ShuaiApiAuth
                    val session = auth.fetchSelf(value, newConfig.userId).getOrElse {
                        ui.printError(it.message ?: "invalid access token")
                        return
                    }
                    auth.activateSession(session).getOrElse {
                        ui.printError(it.message ?: "claim failed")
                        return
                    }
                }
                ui.printSuccess("Setting '$key' updated")
            }
            else -> {
                ui.print("")
                ui.print("Settings commands:")
                ui.print("  /settings list           - Show all settings")
                ui.print("  /settings get <key>      - Get a setting value")
                ui.print("  /settings set <key> <value> - Set a setting value")
                ui.print("")
                ui.print("Keys: model, baseUrl, apiType, accessToken, theme, fastMode")
                ui.print(rj.cocacode.ui.Ansi.dim("Use /login for access-token auth · /console for web management"))
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