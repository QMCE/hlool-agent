package rj.cocacode.parser

import java.io.File

object CodeParser {
    data class ParseResult(
        val ast: ASTNode?,
        val errors: List<ParseError> = emptyList()
    )
    
    data class ParseError(
        val line: Int,
        val column: Int,
        val message: String
    )
    
    sealed class ASTNode {
        abstract val children: List<ASTNode>
    }
    
    data class ProgramNode(override val children: List<ASTNode>) : ASTNode()
    
    data class FunctionNode(
        val name: String,
        val params: List<String>,
        val body: List<ASTNode>,
        override val children: List<ASTNode> = emptyList()
    ) : ASTNode()
    
    data class ClassNode(
        val name: String,
        val members: List<ASTNode>,
        override val children: List<ASTNode> = emptyList()
    ) : ASTNode()
    
    data class ImportNode(
        val path: String,
        val names: List<String>,
        override val children: List<ASTNode> = emptyList()
    ) : ASTNode()
}

object KotlinParser {
    fun parse(content: String): CodeParser.ParseResult {
        val errors = mutableListOf<CodeParser.ParseError>()
        val imports = mutableListOf<CodeParser.ImportNode>()
        val functions = mutableListOf<CodeParser.FunctionNode>()
        val classes = mutableListOf<CodeParser.ClassNode>()
        
        var lineNum = 0
        var braceDepth = 0
        var parenDepth = 0
        var inFunction = false
        var inClass = false
        var currentFunction: String? = null
        var currentClass: String? = null
        val functionBody = mutableListOf<String>()
        val classBody = mutableListOf<CodeParser.ASTNode>()
        
        content.lines().forEach { line ->
            lineNum++
            val trimmed = line.trim()
            
            if (trimmed.startsWith("import ")) {
                val path = trimmed.substringAfter("import ").trimEnd()
                imports.add(CodeParser.ImportNode(path, emptyList()))
            }
            
            if (trimmed.startsWith("fun ")) {
                val match = Regex("fun (\\w+)").find(trimmed)
                if (match != null) {
                    currentFunction = match.groupValues[1]
                    inFunction = true
                }
            }
            
            if (trimmed.startsWith("class ")) {
                val match = Regex("class (\\w+)").find(trimmed)
                if (match != null) {
                    currentClass = match.groupValues[1]
                    inClass = true
                }
            }
            
            braceDepth += trimmed.count { it == '{' }
            braceDepth -= trimmed.count { it == '}' }
            parenDepth += trimmed.count { it == '(' }
            parenDepth -= trimmed.count { it == ')' }
            
            if (braceDepth == 0 && (inFunction || inClass)) {
                if (inFunction && currentFunction != null) {
                    functions.add(CodeParser.FunctionNode(
                        name = currentFunction,
                        params = extractParams(trimmed),
                        body = emptyList()
                    ))
                    currentFunction = null
                    inFunction = false
                }
                
                if (inClass && currentClass != null) {
                    classes.add(CodeParser.ClassNode(
                        name = currentClass,
                        members = classBody.toList()
                    ))
                    classBody.clear()
                    currentClass = null
                    inClass = false
                }
            }
        }
        
        val root = CodeParser.ProgramNode(imports + classes + functions)
        return CodeParser.ParseResult(root, errors)
    }
    
    private fun extractParams(line: String): List<String> {
        val paramsStart = line.indexOf('(')
        val paramsEnd = line.lastIndexOf(')')
        if (paramsStart == -1 || paramsEnd == -1) return emptyList()
        
        val paramsStr = line.substring(paramsStart + 1, paramsEnd)
        return paramsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

object JsonParser {
    fun parse(json: String): Any? {
        return parseValue(json.trim())
    }
    
    private fun parseValue(json: String): Any? {
        val trimmed = json.trim()
        
        return when {
            trimmed.startsWith("{") -> parseObject(trimmed)
            trimmed.startsWith("[") -> parseArray(trimmed)
            trimmed.startsWith("\"") -> parseString(trimmed)
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }
    
    private fun parseObject(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val content = json.trim().removeSurrounding("{", "}").trim()
        if (content.isEmpty()) return result
        
        var depth = 0
        var current = StringBuilder()
        var key = ""
        var parsingKey = true
        
        for (char in content) {
            when (char) {
                '{' -> { depth++; current.append(char) }
                '}' -> { depth--; current.append(char) }
                ':' -> if (depth == 0 && parsingKey) {
                    key = parseString(current.toString().trim())
                    current = StringBuilder()
                    parsingKey = false
                } else { current.append(char) }
                ',' -> if (depth == 0) {
                    val value = parseValue(current.toString().trim())
                    result[key] = value
                    current = StringBuilder()
                    parsingKey = true
                } else { current.append(char) }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            result[key] = parseValue(current.toString().trim())
        }
        
        return result
    }
    
    private fun parseArray(json: String): List<Any?> {
        val result = mutableListOf<Any?>()
        val content = json.trim().removeSurrounding("[", "]").trim()
        if (content.isEmpty()) return result
        
        var depth = 0
        var current = StringBuilder()
        
        for (char in content) {
            when (char) {
                '{', '[' -> { depth++; current.append(char) }
                '}', ']' -> { depth--; current.append(char) }
                ',' -> if (depth == 0) {
                    result.add(parseValue(current.toString().trim()))
                    current = StringBuilder()
                } else { current.append(char) }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            result.add(parseValue(current.toString().trim()))
        }
        
        return result
    }
    
    private fun parseString(json: String): String {
        return json.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}

object HtmlParser {
    data class HtmlNode(
        val tag: String,
        val attributes: Map<String, String> = emptyMap(),
        val children: List<HtmlNode> = emptyList(),
        val text: String = ""
    )
    
    fun parse(html: String): HtmlNode? {
        val cleaned = html.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
        
        return parseNode(cleaned)
    }
    
    private fun parseNode(html: String): HtmlNode? {
        val tagMatch = Regex("<(\\w+)([^>]*)>(.*)</\\1>", RegexOption.DOT_MATCHES_ALL).find(html)
        
        return if (tagMatch != null) {
            val tag = tagMatch.groupValues[1]
            val attrs = parseAttributes(tagMatch.groupValues[2])
            val content = tagMatch.groupValues[3]
            
            HtmlNode(
                tag = tag,
                attributes = attrs,
                text = if (content.isBlank()) "" else content,
                children = parseChildren(content)
            )
        } else {
            null
        }
    }
    
    private fun parseAttributes(attrStr: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        Regex("(\\w+)=[\"\']([^\"\']*)[\"\']").findAll(attrStr).forEach {
            attrs[it.groupValues[1]] = it.groupValues[2]
        }
        return attrs
    }
    
    private fun parseChildren(html: String): List<HtmlNode> {
        val children = mutableListOf<HtmlNode>()
        val cleaned = html.replace(Regex(">\\s+<"), "><")
        
        var depth = 0
        var current = StringBuilder()
        
        for (char in cleaned) {
            when (char) {
                '<' -> {
                    if (current.toString().isNotBlank()) {
                        children.add(HtmlNode("", emptyMap(), emptyList(), current.toString()))
                        current = StringBuilder()
                    }
                    current.append(char)
                    depth++
                }
                '>' -> {
                    current.append(char)
                    depth--
                    if (depth == 0) {
                        parseNode(current.toString())?.let { children.add(it) }
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        
        return children
    }
}