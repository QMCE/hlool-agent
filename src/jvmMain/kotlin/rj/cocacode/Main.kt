package rj.cocacode

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import rj.cocacode.repl.Repl
import rj.cocacode.commands.CommandRegistry

class CocaCodeCLI : CliktCommand(
    name = "cocacode",
    help = "CocaCode - AI Coding Assistant",
    printHelpOnEmptyArgs = true
) {
    val version: Boolean by option("-v", "--version", help = "Show version").flag()
    val interactive: Boolean by option("-i", "--interactive", help = "Start interactive mode").flag()
    val prompt: String? by option("-p", "--prompt", help = "Run a single prompt")
    
    override fun run() {
        when {
            version -> println("CocaCode v1.0.0")
            interactive || prompt != null -> {
                val promptValue = prompt
                if (promptValue != null) {
                    runPrompt(promptValue)
                } else {
                    startRepl()
                }
            }
            else -> {
                println("CocaCode - AI Coding Assistant")
                println("Usage: cocacode [command] [options]")
                println("       cocacode -i        # Start interactive mode")
                println("       cocacode -p 'Hi'   # Run a single prompt")
                println("       cocacode --version")
            }
        }
    }
    
    private fun runPrompt(prompt: String) {
        CommandRegistry.init()
        
        val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
        
        kotlinx.coroutines.runBlocking {
            val response = engine.processQuery(prompt)
            
            when (response) {
                is rj.cocacode.engine.QueryResponse.Success -> {
                    println(response.response.content)
                }
                is rj.cocacode.engine.QueryResponse.Error -> {
                    System.err.println("Error: ${response.message}")
                    System.exit(1)
                }
            }
        }
    }
    
    private fun startRepl() {
        CommandRegistry.init()
        Repl.start()
    }
}

fun main(args: Array<String>) {
    CocaCodeCLI().main(args)
}