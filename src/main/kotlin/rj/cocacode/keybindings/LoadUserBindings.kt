package rj.cocacode.keybindings

data class KeybindingsLoadResult(
    val bindings: List<ParsedBinding>,
    val warnings: List<KeybindingWarning>
)

private var watcher: Any? = null
private var initialized = false
private var disposed = false
private var cachedBindings: List<ParsedBinding>? = null
private var cachedWarnings: List<KeybindingWarning> = emptyList()
private var keybindingsChangedListeners = mutableListOf<(KeybindingsLoadResult) -> Unit>()
private var lastCustomBindingsLogDate: String? = null

fun isKeybindingCustomizationEnabled(): Boolean = false

fun getKeybindingsPath(): String {
    val configDir = System.getenv("COCACODE_CONFIG_DIR")
        ?: System.getenv("CLAUDE_CONFIG_DIR")
        ?: (System.getProperty("user.home") + "/.cocacode")
    return "$configDir/keybindings.json"
}

private fun getDefaultParsedBindings(): List<ParsedBinding> {
    return parseBindings(DEFAULT_BINDINGS)
}

private fun isKeybindingBlock(obj: Any?): Boolean {
    if (obj !is Map<*, *>) return false
    return obj["context"] is String && obj["bindings"] is Map<*, *>
}

fun loadKeybindings(): KeybindingsLoadResult {
    val defaultBindings = getDefaultParsedBindings()
    
    if (!isKeybindingCustomizationEnabled()) {
        return KeybindingsLoadResult(defaultBindings, emptyList())
    }
    
    return KeybindingsLoadResult(defaultBindings, emptyList())
}

fun loadKeybindingsSync(): List<ParsedBinding> {
    if (cachedBindings != null) {
        return cachedBindings!!
    }
    
    val result = loadKeybindingsSyncWithWarnings()
    return result.bindings
}

fun loadKeybindingsSyncWithWarnings(): KeybindingsLoadResult {
    if (cachedBindings != null) {
        return KeybindingsLoadResult(cachedBindings!!, cachedWarnings)
    }
    
    val defaultBindings = getDefaultParsedBindings()
    
    if (!isKeybindingCustomizationEnabled()) {
        cachedBindings = defaultBindings
        cachedWarnings = emptyList()
        return KeybindingsLoadResult(cachedBindings!!, cachedWarnings)
    }
    
    cachedBindings = defaultBindings
    cachedWarnings = emptyList()
    return KeybindingsLoadResult(cachedBindings!!, cachedWarnings)
}

fun initializeKeybindingWatcher() {
    if (initialized || disposed) return
    
    if (!isKeybindingCustomizationEnabled()) {
        return
    }
    
    initialized = true
}

fun disposeKeybindingWatcher() {
    disposed = true
    watcher = null
    keybindingsChangedListeners.clear()
}

fun subscribeToKeybindingChanges(listener: (KeybindingsLoadResult) -> Unit) {
    keybindingsChangedListeners.add(listener)
}

private fun notifyListeners(result: KeybindingsLoadResult) {
    for (listener in keybindingsChangedListeners) {
        listener(result)
    }
}

fun getCachedKeybindingWarnings(): List<KeybindingWarning> {
    return cachedWarnings
}

fun resetKeybindingLoaderForTesting() {
    initialized = false
    disposed = false
    cachedBindings = null
    cachedWarnings = emptyList()
    lastCustomBindingsLogDate = null
    watcher = null
    keybindingsChangedListeners.clear()
}
