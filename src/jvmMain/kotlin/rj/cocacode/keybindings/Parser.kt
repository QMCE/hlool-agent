package rj.cocacode.keybindings

fun parseKeystroke(input: String): ParsedKeystroke {
    val parts = input.split("+")
    var key = ""
    var ctrl = false
    var alt = false
    var shift = false
    var meta = false
    var super_ = false
    
    for (part in parts) {
        val lower = part.lowercase()
        when (lower) {
            "ctrl", "control" -> ctrl = true
            "alt", "opt", "option" -> alt = true
            "shift" -> shift = true
            "meta" -> meta = true
            "cmd", "command", "super", "win" -> super_ = true
            "esc" -> key = "escape"
            "return" -> key = "enter"
            "space" -> key = " "
            "↑" -> key = "up"
            "↓" -> key = "down"
            "←" -> key = "left"
            "→" -> key = "right"
            else -> key = lower
        }
    }
    
    return ParsedKeystroke(key, ctrl, alt, shift, meta, super_)
}

fun parseChord(input: String): Chord {
    if (input == " ") return listOf(parseKeystroke("space"))
    return input.trim().split(Regex("\\s+")).map { parseKeystroke(it) }
}

fun keystrokeToString(ks: ParsedKeystroke): String {
    val parts = mutableListOf<String>()
    if (ks.ctrl) parts.add("ctrl")
    if (ks.alt) parts.add("alt")
    if (ks.shift) parts.add("shift")
    if (ks.meta) parts.add("meta")
    if (ks.super_) parts.add("cmd")
    parts.add(keyToDisplayName(ks.key))
    return parts.joinToString("+")
}

private fun keyToDisplayName(key: String): String {
    return when (key) {
        "escape" -> "Esc"
        " " -> "Space"
        "tab" -> "tab"
        "enter" -> "Enter"
        "backspace" -> "Backspace"
        "delete" -> "Delete"
        "up" -> "↑"
        "down" -> "↓"
        "left" -> "←"
        "right" -> "→"
        "pageup" -> "PageUp"
        "pagedown" -> "PageDown"
        "home" -> "Home"
        "end" -> "End"
        else -> key
    }
}

fun chordToString(chord: Chord): String {
    return chord.joinToString(" ") { keystrokeToString(it) }
}

typealias DisplayPlatform = String

fun keystrokeToDisplayString(ks: ParsedKeystroke, platform: DisplayPlatform = "linux"): String {
    val parts = mutableListOf<String>()
    if (ks.ctrl) parts.add("ctrl")
    if (ks.alt || ks.meta) {
        parts.add(if (platform == "macos") "opt" else "alt")
    }
    if (ks.shift) parts.add("shift")
    if (ks.super_) {
        parts.add(if (platform == "macos") "cmd" else "super")
    }
    parts.add(keyToDisplayName(ks.key))
    return parts.joinToString("+")
}

fun chordToDisplayString(chord: Chord, platform: DisplayPlatform = "linux"): String {
    return chord.joinToString(" ") { keystrokeToDisplayString(it, platform) }
}

fun parseBindings(blocks: List<KeybindingBlock>): List<ParsedBinding> {
    return blocks.flatMap { block ->
        block.bindings.map { (key, action) ->
            ParsedBinding(
                chord = parseChord(key),
                action = action,
                context = block.context
            )
        }
    }
}