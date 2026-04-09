package rj.cocacode.repl

import rj.cocacode.commands.CommandRegistry
import rj.cocacode.engine.QueryEngineManager
import rj.cocacode.state.AppStateManager
import rj.cocacode.ui.UI
import rj.cocacode.utils.ConfigManager
import rj.cocacode.cli.Exit
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.tools.BashTool
import rj.cocacode.tools.ReadFileTool
import rj.cocacode.tools.WriteFileTool
import rj.cocacode.tools.EditFileTool
import rj.cocacode.tools.GlobTool
import rj.cocacode.tools.GrepTool
import kotlinx.coroutines.runBlocking

class ReplLoop {
    private var isRunning = false
    
    fun start() {
        isRunning = true
        
        val config = ConfigManager.getGlobalConfig()
        
        UI.get().print("CocaCode v1.0.0 - AI Coding Assistant")
        UI.get().print("Type /help for available commands")
        UI.get().print("")
        
        while (isRunning) {
            try {
                val input = UI.get().readLine("cocacode> ") ?: continue
                val trimmed = input.trim()
                
                if (trimmed.isEmpty()) continue
                
                runBlocking {
                    processInput(trimmed)
                }
                
            } catch (e: Exception) {
                UI.get().printError("Error: ${e.message}")
            }
        }
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
                AppStateManager.setThinking(true)
                
                val response = engine.processQuery(input)
                
                AppStateManager.setThinking(false)
                
                when (response) {
                    is rj.cocacode.engine.QueryResponse.Success -> {
                        UI.get().print(response.response.content)
                    }
                    is rj.cocacode.engine.QueryResponse.Error -> {
                        UI.get().printError(response.message)
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
        ToolRegistry.register(BashTool())
        ToolRegistry.register(ReadFileTool())
        ToolRegistry.register(WriteFileTool())
        ToolRegistry.register(EditFileTool())
        ToolRegistry.register(GlobTool())
        ToolRegistry.register(GrepTool())
    }
    
    fun start() {
        registerTools()
        CommandRegistry.init()
        
        val loop = ReplLoop()
        loop.start()
    }
}