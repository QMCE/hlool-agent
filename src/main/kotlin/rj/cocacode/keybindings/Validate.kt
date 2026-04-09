package rj.cocacode.keybindings

enum class KeybindingWarningType {
    PARSE_ERROR,
    DUPLICATE,
    RESERVED,
    INVALID_CONTEXT,
    INVALID_ACTION
}

enum class WarningSeverity {
    ERROR,
    WARNING
}

data class KeybindingWarning(
    val type: KeybindingWarningType,
    val severity: WarningSeverity,
    val message: String,
    val key: String? = null,
    val context: String? = null,
    val action: String? = null,
    val suggestion: String? = null
)

private val VALID_CONTEXTS = listOf(
    KeybindingContextName.Global,
    KeybindingContextName.Chat,
    KeybindingContextName.Autocomplete,
    KeybindingContextName.Confirmation,
    KeybindingContextName.Help,
    KeybindingContextName.Transcript,
    KeybindingContextName.HistorySearch,
    KeybindingContextName.Task,
    KeybindingContextName.ThemePicker,
    KeybindingContextName.Settings,
    KeybindingContextName.Tabs,
    KeybindingContextName.Attachments,
    KeybindingContextName.Footer,
    KeybindingContextName.MessageSelector,
    KeybindingContextName.DiffDialog,
    KeybindingContextName.ModelPicker,
    KeybindingContextName.Select,
    KeybindingContextName.Plugin
)

private fun isValidContext(value: String): Boolean {
    return VALID_CONTEXTS.any { it.name == value }
}

private fun validateKeystroke(keystroke: String): KeybindingWarning? {
    val parts = keystroke.lowercase().split("+")
    
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) {
            return KeybindingWarning(
                type = KeybindingWarningType.PARSE_ERROR,
                severity = WarningSeverity.ERROR,
                message = "Empty key part in \"$keystroke\"",
                key = keystroke,
                suggestion = "Remove extra \"+\" characters"
            )
        }
    }
    
    val parsed = parseKeystroke(keystroke)
    if (parsed.key.isEmpty() && !parsed.ctrl && !parsed.alt && !parsed.shift && !parsed.meta) {
        return KeybindingWarning(
            type = KeybindingWarningType.PARSE_ERROR,
            severity = WarningSeverity.ERROR,
            message = "Could not parse keystroke \"$keystroke\"",
            key = keystroke
        )
    }
    
    return null
}

private fun validateBlock(block: Map<String, Any?>, blockIndex: Int): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    
    val contextValue = block["context"]
    var contextName: String? = null
    
    if (contextValue !is String) {
        warnings.add(
            KeybindingWarning(
                type = KeybindingWarningType.PARSE_ERROR,
                severity = WarningSeverity.ERROR,
                message = "Keybinding block ${blockIndex + 1} missing \"context\" field"
            )
        )
    } else if (!isValidContext(contextValue)) {
        warnings.add(
            KeybindingWarning(
                type = KeybindingWarningType.INVALID_CONTEXT,
                severity = WarningSeverity.ERROR,
                message = "Unknown context \"$contextValue\"",
                context = contextValue,
                suggestion = "Valid contexts: ${VALID_CONTEXTS.joinToString(", ") { it.name }}"
            )
        )
    } else {
        contextName = contextValue
    }
    
    val bindingsValue = block["bindings"]
    if (bindingsValue !is Map<*, *>) {
        warnings.add(
            KeybindingWarning(
                type = KeybindingWarningType.PARSE_ERROR,
                severity = WarningSeverity.ERROR,
                message = "Keybinding block ${blockIndex + 1} missing \"bindings\" field"
            )
        )
        return warnings
    }
    
    val bindings = bindingsValue as Map<String, Any?>
    for ((key, action) in bindings) {
        val keyError = validateKeystroke(key)
        if (keyError != null) {
            keyError.let { 
                warnings.add(it.copy(context = contextName))
            }
        }
        
        if (action != null && action !is String) {
            warnings.add(
                KeybindingWarning(
                    type = KeybindingWarningType.INVALID_ACTION,
                    severity = WarningSeverity.ERROR,
                    message = "Invalid action for \"$key\": must be a string or null",
                    key = key,
                    context = contextName
                )
            )
        } else if (action is String && action.startsWith("command:")) {
            if (!Regex("^command:[a-zA-Z0-9:\\-_]+$").matches(action)) {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.INVALID_ACTION,
                        severity = WarningSeverity.WARNING,
                        message = "Invalid command binding \"$action\" for \"$key\": command name may only contain alphanumeric characters, colons, hyphens, and underscores",
                        key = key,
                        context = contextName,
                        action = action
                    )
                )
            }
            if (contextName != null && contextName != "Chat") {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.INVALID_ACTION,
                        severity = WarningSeverity.WARNING,
                        message = "Command binding \"$action\" must be in \"Chat\" context, not \"$contextName\"",
                        key = key,
                        context = contextName,
                        action = action,
                        suggestion = "Move this binding to a block with \"context\": \"Chat\""
                    )
                )
            }
        } else if (action == "voice:pushToTalk") {
            val ks = parseChord(key).firstOrNull()
            if (ks != null &&
                !ks.ctrl &&
                !ks.alt &&
                !ks.shift &&
                !ks.meta &&
                !ks.super_ &&
                Regex("^[a-z]$").matches(ks.key)
            ) {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.INVALID_ACTION,
                        severity = WarningSeverity.WARNING,
                        message = "Binding \"$key\" to voice:pushToTalk prints into the input during warmup; use space or a modifier combo like meta+k",
                        key = key,
                        context = contextName,
                        action = action
                    )
                )
            }
        }
    }
    
    return warnings
}

fun checkDuplicateKeysInJson(jsonString: String): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    
    val bindingsBlockPattern = Regex("\"bindings\"\\s*:\\s*\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}")
    val blockMatches = bindingsBlockPattern.findAll(jsonString)
    
    for (blockMatch in blockMatches) {
        val blockContent = blockMatch.groupValues.getOrNull(1) ?: continue
        if (blockContent.isEmpty()) continue
        
        val textBeforeBlock = jsonString.substring(0, blockMatch.range.first)
        val contextMatch = Regex("\"context\"\\s*:\\s*\"([^\"]+)\"[^{]*$").find(textBeforeBlock)
        val context = contextMatch?.groupValues?.getOrNull(1) ?: "unknown"
        
        val keyPattern = Regex("\"([^\"]+)\"\\s*:")
        val keysByName = mutableMapOf<String, Int>()
        
        for (keyMatch in keyPattern.findAll(blockContent)) {
            val key = keyMatch.groupValues.getOrNull(1) ?: continue
            if (key.isEmpty()) continue
            
            val count = (keysByName[key] ?: 0) + 1
            keysByName[key] = count
            
            if (count == 2) {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.DUPLICATE,
                        severity = WarningSeverity.WARNING,
                        message = "Duplicate key \"$key\" in $context bindings",
                        key = key,
                        context = context,
                        suggestion = "This key appears multiple times in the same context. JSON uses the last value, earlier values are ignored."
                    )
                )
            }
        }
    }
    
    return warnings
}

fun validateUserConfig(userBlocks: Any?): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    
    if (userBlocks !is List<*>) {
        warnings.add(
            KeybindingWarning(
                type = KeybindingWarningType.PARSE_ERROR,
                severity = WarningSeverity.ERROR,
                message = "keybindings.json must contain an array",
                suggestion = "Wrap your bindings in [ ]"
            )
        )
        return warnings
    }
    
    for (i in userBlocks.indices) {
        val block = userBlocks[i]
        if (block is Map<*, *>) {
            val stringKeyMap = block.entries.mapNotNull { (it.key as? String)?.let { k -> k to it.value } }.toMap()
            warnings.addAll(validateBlock(stringKeyMap, i))
        }
    }
    
    return warnings
}

fun checkDuplicates(blocks: List<KeybindingBlock>): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    val seenByContext = mutableMapOf<String, MutableMap<String, String>>()
    
    for (block in blocks) {
        val contextMap = seenByContext.getOrPut(block.context.name) { mutableMapOf() }
        
        for ((key, action) in block.bindings) {
            val normalizedKey = normalizeKeyForComparison(key)
            val existingAction = contextMap[normalizedKey]
            
            if (existingAction != null && existingAction != action) {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.DUPLICATE,
                        severity = WarningSeverity.WARNING,
                        message = "Duplicate binding \"$key\" in ${block.context} context",
                        key = key,
                        context = block.context.name,
                        action = action ?: "null (unbind)",
                        suggestion = "Previously bound to \"$existingAction\". Only the last binding will be used."
                    )
                )
            }
            
            contextMap[normalizedKey] = action ?: "null"
        }
    }
    
    return warnings
}

fun checkReservedShortcuts(bindings: List<ParsedBinding>): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    val reserved = getReservedShortcuts()
    
    for (binding in bindings) {
        val keyDisplay = chordToString(binding.chord)
        val normalizedKey = normalizeKeyForComparison(keyDisplay)
        
        for (res in reserved) {
            if (normalizeKeyForComparison(res.key) == normalizedKey) {
                warnings.add(
                    KeybindingWarning(
                        type = KeybindingWarningType.RESERVED,
                        severity = if (res.severity == "error") WarningSeverity.ERROR else WarningSeverity.WARNING,
                        message = "\"$keyDisplay\" may not work: ${res.reason}",
                        key = keyDisplay,
                        context = binding.context.name,
                        action = binding.action
                    )
                )
            }
        }
    }
    
    return warnings
}

private fun getUserBindingsForValidation(userBlocks: List<KeybindingBlock>): List<ParsedBinding> {
    return userBlocks.flatMap { block ->
        block.bindings.map { (key, action) ->
            ParsedBinding(
                chord = key.split(" ").map { parseKeystroke(it) },
                action = action,
                context = block.context
            )
        }
    }
}

fun validateBindings(userBlocks: Any?, parsedBindings: List<ParsedBinding>): List<KeybindingWarning> {
    val warnings = mutableListOf<KeybindingWarning>()
    
    warnings.addAll(validateUserConfig(userBlocks))
    
    if (userBlocks is List<*>) {
        val keybindingBlocks = userBlocks.mapNotNull { block ->
            if (block is Map<*, *>) {
                val context = (block["context"] as? String)?.let { 
                    try { KeybindingContextName.valueOf(it) } catch (e: Exception) { null }
                }
                val bindings = (block["bindings"] as? Map<*, *>)?.mapKeys { it.key as String }?.mapValues { it.value as? String }
                if (context != null && bindings != null) {
                    KeybindingBlock(context, bindings)
                } else null
            } else null
        }
        
        warnings.addAll(checkDuplicates(keybindingBlocks))
        
        val userBindings = getUserBindingsForValidation(keybindingBlocks)
        warnings.addAll(checkReservedShortcuts(userBindings))
    }
    
    val seen = mutableSetOf<String>()
    return warnings.filter { w ->
        val key = "${w.type}:${w.key}:${w.context}"
        if (seen.contains(key)) false
        else {
            seen.add(key)
            true
        }
    }
}

fun formatWarning(warning: KeybindingWarning): String {
    val icon = if (warning.severity == WarningSeverity.ERROR) "✗" else "⚠"
    var msg = "$icon Keybinding ${warning.severity.name.lowercase()}: ${warning.message}"
    
    if (warning.suggestion != null) {
        msg += "\n  ${warning.suggestion}"
    }
    
    return msg
}

fun formatWarnings(warnings: List<KeybindingWarning>): String {
    if (warnings.isEmpty()) return ""
    
    val errors = warnings.filter { it.severity == WarningSeverity.ERROR }
    val warns = warnings.filter { it.severity == WarningSeverity.WARNING }
    
    val lines = mutableListOf<String>()
    
    if (errors.isNotEmpty()) {
        lines.add("Found ${errors.size} keybinding ${if (errors.size == 1) "error" else "errors"}:")
        for (e in errors) {
            lines.add(formatWarning(e))
        }
    }
    
    if (warns.isNotEmpty()) {
        if (lines.isNotEmpty()) lines.add("")
        lines.add("Found ${warns.size} keybinding ${if (warns.size == 1) "warning" else "warnings"}:")
        for (w in warns) {
            lines.add(formatWarning(w))
        }
    }
    
    return lines.joinToString("\n")
}
