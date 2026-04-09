package rj.cocacode.keybindings

fun filterReservedShortcuts(blocks: List<KeybindingBlock>): List<KeybindingBlock> {
    val reservedKeys = NON_REBINDABLE.map { normalizeKeyForComparison(it.key) }.toSet()
    
    return blocks
        .map { block ->
            val filteredBindings = block.bindings.filter { (key, _) ->
                !reservedKeys.contains(normalizeKeyForComparison(key))
            }
            KeybindingBlock(block.context, filteredBindings)
        }
        .filter { it.bindings.isNotEmpty() }
}

fun generateKeybindingsTemplate(): String {
    val bindings = filterReservedShortcuts(DEFAULT_BINDINGS)
    
    val config = mapOf(
        "\$schema" to "https://www.schemastore.org/claude-code-keybindings.json",
        "\$docs" to "https://code.claude.com/docs/en/keybindings",
        "bindings" to bindings
    )
    
    return jsonStringify(config) + "\n"
}

private fun jsonStringify(obj: Any?, indent: Int = 0): String {
    return when (obj) {
        null -> "null"
        is String -> "\"${obj.replace("\"", "\\\"")}\""
        is Number, is Boolean -> obj.toString()
        is Map<*, *> -> {
            val entries = obj.entries.map { (k, v) -> 
                "${jsonStringify(k)}: ${jsonStringify(v)}" 
            }
            if (indent == 0) "{${entries.joinToString(", ")}}"
            else "{\n${entries.joinToString(",\n")}\n${" ".repeat(indent)}}"
        }
        is List<*> -> {
            val items = obj.map { jsonStringify(it) }
            "[${items.joinToString(", ")}]"
        }
        is KeybindingBlock -> {
            "{\"context\": \"${obj.context.name}\", \"bindings\": ${jsonStringify(obj.bindings)}}"
        }
        else -> obj.toString()
    }
}
