package rj.cocacode

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val resume: Boolean by option("--resume", "--continue", help = "Resume the most recent session").flag()

    override fun run() {
        when {
            version -> println("CocaCode v${rj.cocacode.BuildKonfig.APP_VERSION}")
            interactive || prompt != null -> {
                val promptValue = prompt
                if (promptValue != null) {
                    runPrompt(promptValue, resume)
                } else {
                    startRepl(resume)
                }
            }
            else -> {
                println("CocaCode - AI Coding Assistant")
                println("Usage: cocacode [command] [options]")
                println("       cocacode -i           # Start interactive mode")
                println("       cocacode --resume     # Resume the last session")
                println("       cocacode -p 'Hi'      # Run a single prompt")
                println("       cocacode --version")
            }
        }
    }

    private fun runPrompt(prompt: String, resume: Boolean = false) {
        CommandRegistry.init()
        rj.cocacode.repl.Repl.registerTools()

        val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
        if (resume) engine.restoreLastSession()
        val out = java.io.PrintWriter(System.out, true)
        val statusBar = rj.cocacode.ui.StatusBar(out)
        val renderer = rj.cocacode.ui.StreamRenderer(out, statusBar)
        val startTime = System.currentTimeMillis()

        kotlinx.coroutines.runBlocking {
            statusBar.show(rj.cocacode.ui.StatusBar.formatStatus("Musing…", 0, 0))

            // Live status updater.
            val updater = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                while (isActive) {
                    kotlinx.coroutines.delay(200)
                    val elapsed = System.currentTimeMillis() - startTime
                    statusBar.update(rj.cocacode.ui.StatusBar.formatStatus("Musing…", elapsed, renderer.estimatedTokens()))
                }
            }

            val response = engine.processQueryStreaming(
                prompt,
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
            out.print("\n")
            out.flush()

            when (response) {
                is rj.cocacode.engine.QueryResponse.Success -> Unit // already streamed
                is rj.cocacode.engine.QueryResponse.Error -> {
                    System.err.println("Error: ${response.message}")
                    System.exit(1)
                }
            }
        }
    }
    
    private fun startRepl(resume: Boolean = false) {
        CommandRegistry.init()
        Repl.start(resume)
    }
}

fun main(args: Array<String>) {
    rj.cocacode.config.ApiConfig.reload()
    CocaCodeCLI().main(args)
}