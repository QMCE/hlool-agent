package rj.cocacode.ui

import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Terminal
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import rj.cocacode.constants.Figures
import rj.cocacode.state.AppStateManager

class TerminalUI {
    private var terminal: Terminal? = null
    private var reader: LineReader? = null
    
    fun initialize() {
        terminal = TerminalBuilder.builder()
            .jna(true)
            .build()
        
        reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer { _, line, candidates ->
                val cmd = line.line().split(" ").lastOrNull() ?: ""
                rj.cocacode.commands.CommandRegistry.getNames()
                    .filter { it.startsWith(cmd) }
                    .forEach { candidates.add(org.jline.reader.Candidate(it)) }
            }
            .build()
    }
    
    fun readLine(prompt: String = "cocacode> "): String? {
        return reader?.readLine(prompt)
    }
    
    fun print(message: String) {
        terminal?.writer()?.println(message)
    }
    
    fun printError(message: String) {
        terminal?.writer()?.println("${Figures.BLACK_CIRCLE} $message")
    }
    
    fun printSuccess(message: String) {
        terminal?.writer()?.println("✓ $message")
    }
    
    fun clear() {
        terminal?.writer()?.print("\u001b[H\u001b[2J")
    }
    
    fun showSpinner(message: String) {
        var frame = 0
        terminal?.writer()?.print("$message ${Figures.BRIDGE_SPINNER_FRAMES[frame]}")
    }
    
    fun updateSpinner(frame: Int) {
        terminal?.writer()?.print("\r${Figures.BRIDGE_SPINNER_FRAMES[frame % Figures.BRIDGE_SPINNER_FRAMES.size]}")
    }
    
    fun stopSpinner() {
        terminal?.writer()?.println()
    }
    
    fun drawBox(title: String, content: List<String>) {
        val width = 60
        val border = "━".repeat(width - 2)
        
        print("┌$border┐")
        print("│ ${title.padEnd(width - 4)} │")
        print("├$border┤")
        
        content.forEach { line ->
            print("│ ${line.padEnd(width - 4)} │")
        }
        
        print("└$border┘")
    }
    
    fun drawTable(headers: List<String>, rows: List<List<String>>) {
        val colWidths = headers.indices.map { col ->
            val maxContent = rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            maxOf(headers[col].length, maxContent) + 2
        }
        
        print(colWidths.mapIndexed { i, w -> headers[i].padEnd(w) }.joinToString("│"))
        print(colWidths.map { "─".repeat(it) }.joinToString("┼"))
        
        rows.forEach { row ->
            print(row.mapIndexed { i, cell -> cell.padEnd(colWidths[i]) }.joinToString("│"))
        }
    }
    
    fun shutdown() {
        terminal?.close()
    }
}

class Spinner(private val message: String) {
    private var isRunning = false
    private val frames = Figures.BRIDGE_SPINNER_FRAMES
    private var frameIndex = 0
    
    fun start() {
        isRunning = true
        Thread {
            while (isRunning) {
                print("\r$message ${frames[frameIndex % frames.size]}")
                frameIndex++
                Thread.sleep(100)
            }
        }.start()
    }
    
    fun stop(success: Boolean = true) {
        isRunning = false
        val indicator = if (success) Figures.BRIDGE_READY_INDICATOR else Figures.BRIDGE_FAILED_INDICATOR
        println("\r$message $indicator")
    }
}

object UI {
    private var instance: TerminalUI? = null
    
    fun init(): TerminalUI {
        if (instance == null) {
            instance = TerminalUI()
            instance!!.initialize()
        }
        return instance!!
    }
    
    fun get(): TerminalUI = instance ?: init()
    
    fun shutdown() {
        instance?.shutdown()
        instance = null
    }
}