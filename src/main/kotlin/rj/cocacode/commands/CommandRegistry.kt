package rj.cocacode.commands

import kotlinx.coroutines.runBlocking

sealed class Command {
    abstract val name: String
    abstract val description: String
    abstract suspend fun execute(args: List<String>)
}

object CommandRegistry {
    private val commands = mutableMapOf<String, Command>()
    
    fun init() {
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

class ExitCommand : Command() {
    override val name = "exit"
    override val description = "Exit the application"
    override suspend fun execute(args: List<String>) {
        kotlin.system.exitProcess(0)
    }
}