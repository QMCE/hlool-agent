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
        register(ExitCommand())
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
    override val description = "Show available commands"
    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        ui.print("")
        ui.print("Available commands:")
        ui.print("")
        CommandRegistry.getAll().forEach { cmd ->
            ui.print("  /${cmd.name.padEnd(12)} ${cmd.description}")
        }
        ui.print("")
        ui.print("For tool help, type: /tools")
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
        ToolRegistry.getAll().forEach { tool ->
            ui.print("  ${tool.name.padEnd(16)} ${tool.description}")
        }
        ui.print("")
    }
}

class ClearCommand : Command() {
    override val name = "clear"
    override val description = "Clear the screen"
    override suspend fun execute(args: List<String>) {
        UI.get().clear()
    }
}

class ExitCommand : Command() {
    override val name = "exit"
    override val description = "Exit the application"
    override suspend fun execute(args: List<String>) {
        kotlin.system.exitProcess(0)
    }
}