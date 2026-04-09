package rj.cocacode.keybindings

data class InkModifiers(
    val ctrl: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val super_: Boolean
)

fun getKeyName(input: String, key: InkKey): String? {
    return when {
        key.escape -> "escape"
        key.`return` -> "enter"
        key.tab -> "tab"
        key.backspace -> "backspace"
        key.delete -> "delete"
        key.upArrow -> "up"
        key.downArrow -> "down"
        key.leftArrow -> "left"
        key.rightArrow -> "right"
        key.pageUp -> "pageup"
        key.pageDown -> "pagedown"
        key.wheelUp -> "wheelup"
        key.wheelDown -> "wheeldown"
        key.home -> "home"
        key.end -> "end"
        input.length == 1 -> input.lowercase()
        else -> null
    }
}

fun getInkModifiers(key: InkKey): InkModifiers {
    return InkModifiers(
        ctrl = key.ctrl,
        shift = key.shift,
        meta = key.meta,
        super_ = key.super_
    )
}

fun modifiersMatch(inkMods: InkModifiers, target: ParsedKeystroke): Boolean {
    if (inkMods.ctrl != target.ctrl) return false
    if (inkMods.shift != target.shift) return false
    
    val targetNeedsMeta = target.alt || target.meta
    if (inkMods.meta != targetNeedsMeta) return false
    
    if (inkMods.super_ != target.super_) return false
    
    return true
}

fun matchesKeystroke(input: String, key: InkKey, target: ParsedKeystroke): Boolean {
    val keyName = getKeyName(input, key)
    if (keyName != target.key) return false
    
    val inkMods = getInkModifiers(key)
    
    if (key.escape) {
        return modifiersMatch(inkMods.copy(meta = false), target)
    }
    
    return modifiersMatch(inkMods, target)
}

fun matchesBinding(input: String, key: InkKey, binding: ParsedBinding): Boolean {
    if (binding.chord.size != 1) return false
    val keystroke = binding.chord.getOrNull(0) ?: return false
    return matchesKeystroke(input, key, keystroke)
}

data class InkKey(
    val escape: Boolean = false,
    val `return`: Boolean = false,
    val tab: Boolean = false,
    val backspace: Boolean = false,
    val delete: Boolean = false,
    val upArrow: Boolean = false,
    val downArrow: Boolean = false,
    val leftArrow: Boolean = false,
    val rightArrow: Boolean = false,
    val pageUp: Boolean = false,
    val pageDown: Boolean = false,
    val wheelUp: Boolean = false,
    val wheelDown: Boolean = false,
    val home: Boolean = false,
    val end: Boolean = false,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
    val super_: Boolean = false
)
