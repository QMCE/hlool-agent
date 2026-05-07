package rj.cocacode.format

object CodeFormatter {
    data class FormatOptions(
        val indentSize: Int = 4,
        val useSpaces: Boolean = true,
        val maxLineLength: Int = 100,
        val insertFinalNewline: Boolean = true
    )
    
    fun format(content: String, language: String, options: FormatOptions = FormatOptions()): String {
        return when (language.lowercase()) {
            "kotlin" -> formatKotlin(content, options)
            "java" -> formatJava(content, options)
            "typescript", "javascript" -> formatJs(content, options)
            "python" -> formatPython(content, options)
            else -> content
        }
    }
    
    private fun formatKotlin(content: String, options: FormatOptions): String {
        val indent = if (options.useSpaces) " ".repeat(options.indentSize) else "\t"
        var inMultilineComment = false
        val lines = content.lines()
        val result = mutableListOf<String>()
        var braceDepth = 0
        
        for (line in lines) {
            var trimmed = line.trim()
            
            if (trimmed.startsWith("/*")) inMultilineComment = true
            if (inMultilineComment) {
                result.add(line)
                if (trimmed.contains("*/")) inMultilineComment = false
                continue
            }
            
            if (trimmed.contains("{")) braceDepth++
            if (trimmed.contains("}")) braceDepth--
            
            if (trimmed.startsWith("}") || trimmed.startsWith("*/")) {
                result.add(indent.repeat(maxOf(0, braceDepth - 1)) + trimmed)
            } else {
                result.add(indent.repeat(braceDepth) + trimmed)
            }
        }
        
        var formatted = result.joinToString("\n")
        
        if (options.insertFinalNewline && !formatted.endsWith("\n")) {
            formatted += "\n"
        }
        
        return formatted
    }
    
    private fun formatJava(content: String, options: FormatOptions): String {
        return formatKotlin(content, options)
    }
    
    private fun formatJs(content: String, options: FormatOptions): String {
        val indent = if (options.useSpaces) " ".repeat(options.indentSize) else "\t"
        var braceDepth = 0
        var inString = false
        var stringChar = ' '
        val result = StringBuilder()
        
        for (char in content) {
            if (!inString) {
                when (char) {
                    '{' -> {
                        braceDepth++
                        result.append(" {\n")
                        result.append(indent.repeat(braceDepth))
                    }
                    '}' -> {
                        braceDepth--
                        result.append("\n")
                        result.append(indent.repeat(braceDepth))
                        result.append('}')
                    }
                    ';' -> {
                        result.append(";\n")
                        result.append(indent.repeat(braceDepth))
                    }
                    '"', '\'' -> {
                        inString = true
                        stringChar = char
                        result.append(char)
                    }
                    else -> result.append(char)
                }
            } else {
                result.append(char)
                if (char == stringChar && content[content.indexOf(char) - 1] != '\\') {
                    inString = false
                }
            }
        }
        
        return result.toString()
    }
    
    private fun formatPython(content: String, options: FormatOptions): String {
        val indent = if (options.useSpaces) " ".repeat(options.indentSize) else "\t"
        var currentIndent = 0
        val result = mutableListOf<String>()
        
        for (line in content.lines()) {
            val trimmed = line.trim()
            
            if (trimmed.isEmpty()) {
                result.add("")
                continue
            }
            
            if (trimmed.startsWith("else") || trimmed.startsWith("elif")) {
                currentIndent = maxOf(0, currentIndent - 1)
            }
            
            result.add(indent.repeat(currentIndent) + trimmed)
            
            if (trimmed.endsWith(":")) {
                currentIndent++
            }
            
            if (trimmed.contains("return") || trimmed.contains("break") || trimmed.contains("continue")) {
                currentIndent = maxOf(0, currentIndent - 1)
            }
        }
        
        return result.joinToString("\n")
    }
}

object PrettyPrinter {
    fun prettyPrint(value: Any, indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        
        return when (value) {
            is Map<*, *> -> {
                val entries = value.entries.joinToString(",\n") { (k, v) ->
                    "${indentStr}  $k: ${prettyPrint(v!!, indent + 1)}"
                }
                "{\n$entries\n$indentStr}"
            }
            is List<*> -> {
                val items = value.joinToString(",\n") { prettyPrint(it!!, indent + 1) }
                "[\n$indentStr  $items\n$indentStr]"
            }
            is String -> "\"$value\""
            else -> value.toString()
        }
    }
}