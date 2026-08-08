package rj.cocacode.commands

import rj.cocacode.agents.Teammate
import rj.cocacode.agents.TeammateManager
import rj.cocacode.ui.Ansi
import rj.cocacode.ui.TerminalUI
import rj.cocacode.ui.UI

/**
 * `/teammate` — spawn and manage teammates.
 *
 * Usage:
 *   /teammate                          list active teammates
 *   /teammate list                     list active teammates
 *   /teammate spawn <name> <task...>   spawn a teammate (type via -t <type>)
 *   /teammate "task..."                spawn with an auto-generated name
 *   /teammate send <name> <msg...>     message a teammate
 *   /teammate output <name>            show a teammate's latest output
 *   /teammate shutdown <name>          gracefully stop a teammate
 *   /teammate kill <name>              force-stop a teammate
 */
class TeammateCommand : Command() {
    override val name = "teammate"
    override val description = "Spawn and manage teammate agents"

    override suspend fun execute(args: List<String>) {
        val ui = UI.get()
        val cmd = args.firstOrNull()
        when {
            cmd == null || cmd == "list" || cmd == "ls" -> listTeammates(ui)
            cmd == "spawn" -> spawn(args.drop(1), ui)
            cmd == "send" || cmd == "msg" -> send(args.drop(1), ui)
            cmd == "output" || cmd == "out" -> output(args.getOrNull(1), ui)
            cmd == "shutdown" || cmd == "stop" -> shutdown(args.getOrNull(1), ui)
            cmd == "kill" -> kill(args.getOrNull(1), ui)
            cmd == "help" -> help(ui)
            else -> {
                // Bare task text -> spawn with an auto-generated name.
                spawn(listOf("teammate-" + System.currentTimeMillis().toString().takeLast(6)) + args, ui)
            }
        }
    }

    private fun listTeammates(ui: TerminalUI) {
        val all = TeammateManager.list()
        ui.print("")
        ui.print(Ansi.bold("Teammates (${all.size} active)"))
        ui.print(Ansi.gray("─".repeat(30)))
        if (all.isEmpty()) {
            ui.print(Ansi.gray("  No teammates running. Use /teammate spawn <name> \"task\""))
            ui.print("")
            return
        }
        all.forEach { t ->
            val nameColored = coloredName(t)
            val statusColored = when (t.status) {
                Teammate.Status.RUNNING -> Ansi.brightYellow("running")
                Teammate.Status.IDLE -> Ansi.gray("idle")
                Teammate.Status.COMPLETED -> Ansi.brightGreen("done")
                Teammate.Status.FAILED -> Ansi.brightRed("failed")
                Teammate.Status.KILLED -> Ansi.dim("killed")
            }
            ui.print("  $nameColored ${statusColored}  ${Ansi.gray(t.prompt.take(50))}")
        }
        ui.print("")
        ui.print(Ansi.gray("  Use /teammate output <name> to see results, /teammate kill <name> to stop."))
        ui.print("")
    }

    private fun spawn(args: List<String>, ui: TerminalUI) {
        // Optional -t <type>; the rest is name + task.
        var type = "general-purpose"
        val rest = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            if (args[i] == "-t" || args[i] == "--type") {
                type = args.getOrNull(i + 1) ?: type
                i += 2
            } else {
                rest.add(args[i])
                i++
            }
        }
        if (rest.isEmpty()) {
            ui.printError("Usage: /teammate spawn <name> \"task\" [-t agentType]")
            return
        }
        val name = rest[0]
        val task = rest.drop(1).joinToString(" ")
        if (task.isBlank()) {
            ui.printError("Task is required: /teammate spawn <name> \"task\"")
            return
        }

        val result = TeammateManager.spawn(name, task, type)
        result.onSuccess { teammate ->
            ui.print("${Ansi.brightGreen("✦")} Spawned teammate ${coloredName(teammate)} ${Ansi.gray("($type)")}")
            ui.print(Ansi.gray("  Task: ${teammate.prompt}"))
            ui.print(Ansi.gray("  Watch with /teammate output ${teammate.name}"))
        }.onFailure { e ->
            ui.printError(e.message ?: "Failed to spawn teammate")
        }
    }

    private fun send(args: List<String>, ui: TerminalUI) {
        if (args.size < 2) {
            ui.printError("Usage: /teammate send <name> <message...>")
            return
        }
        val name = args[0]
        val message = args.drop(1).joinToString(" ")
        val ok = TeammateManager.send(name, message)
        if (ok) {
            ui.print("${Ansi.brightGreen("→")} Message sent to ${coloredName(TeammateManager.get(name)!!)}")
        } else {
            ui.printError("No teammate named '$name'")
        }
    }

    private fun output(name: String?, ui: TerminalUI) {
        if (name == null) {
            ui.printError("Usage: /teammate output <name>")
            return
        }
        val teammate = TeammateManager.get(name)
        if (teammate == null) {
            ui.printError("No teammate named '$name'")
            return
        }
        ui.print("")
        ui.print("${Ansi.bold("──")} ${coloredName(teammate)} ${Ansi.gray("— " + teammate.status.name.lowercase())}")
        if (teammate.output.isBlank()) {
            ui.print(Ansi.gray("  (no output yet)"))
        } else {
            teammate.output.lines().forEach { ui.print("  $it") }
        }
        if (teammate.error != null) {
            ui.print(Ansi.brightRed("  error: ${teammate.error}"))
        }
        ui.print("")
    }

    private fun shutdown(name: String?, ui: TerminalUI) {
        if (name == null) {
            ui.printError("Usage: /teammate shutdown <name>")
            return
        }
        if (TeammateManager.requestShutdown(name)) {
            ui.print(Ansi.gray("Requested shutdown of '$name'"))
        } else {
            ui.printError("No teammate named '$name'")
        }
    }

    private fun kill(name: String?, ui: TerminalUI) {
        if (name == null) {
            ui.printError("Usage: /teammate kill <name>")
            return
        }
        if (TeammateManager.kill(name)) {
            ui.print("${Ansi.brightRed("✕")} Killed teammate '$name'")
        } else {
            ui.printError("No teammate named '$name'")
        }
    }

    private fun help(ui: TerminalUI) {
        ui.print("")
        ui.print(Ansi.bold("Usage:"))
        ui.print("  ${Ansi.brightCyan("/teammate")}${Ansi.gray("                    list teammates")}")
        ui.print("  ${Ansi.brightCyan("/teammate spawn <name> \"task\"")}${Ansi.gray("  spawn a teammate (optional -t type)")}")
        ui.print("  ${Ansi.brightCyan("/teammate \"task\"")}${Ansi.gray("             spawn with auto name")}")
        ui.print("  ${Ansi.brightCyan("/teammate send <name> <msg>")}${Ansi.gray("    message a teammate")}")
        ui.print("  ${Ansi.brightCyan("/teammate output <name>")}${Ansi.gray("       show a teammate's output")}")
        ui.print("  ${Ansi.brightCyan("/teammate kill <name>")}${Ansi.gray("            stop a teammate")}")
        ui.print("")
    }

    private fun coloredName(t: Teammate): String = when (t.color) {
        "red" -> Ansi.brightRed(t.name)
        "blue" -> Ansi.brightBlue(t.name)
        "green" -> Ansi.brightGreen(t.name)
        "yellow" -> Ansi.brightYellow(t.name)
        "purple" -> Ansi.brightMagenta(t.name)
        "orange" -> Ansi.brightYellow(t.name)
        "pink" -> Ansi.brightMagenta(t.name)
        "cyan" -> Ansi.brightCyan(t.name)
        else -> Ansi.brightCyan(t.name)
    }
}
