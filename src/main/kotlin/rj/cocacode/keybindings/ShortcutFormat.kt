package rj.cocacode.keybindings

private val LOGGED_FALLBACKS = mutableSetOf<String>()

fun getShortcutDisplay(
    action: String,
    context: KeybindingContextName,
    fallback: String
): String {
    val bindings = loadKeybindingsSync()
    val resolved = getBindingDisplayText(action, context, bindings)
    if (resolved == null) {
        val key = "$action:${context.name}"
        if (!LOGGED_FALLBACKS.contains(key)) {
            LOGGED_FALLBACKS.add(key)
        }
        return fallback
    }
    return resolved
}
