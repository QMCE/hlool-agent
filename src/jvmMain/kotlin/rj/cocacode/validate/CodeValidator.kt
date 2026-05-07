package rj.cocacode.validate

import java.io.File

object CodeValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<ValidationWarning> = emptyList()
    )
    
    data class ValidationError(
        val line: Int,
        val column: Int,
        val message: String,
        val code: String
    )
    
    data class ValidationWarning(
        val line: Int,
        val column: Int,
        val message: String,
        val code: String
    )
    
    fun validate(content: String, language: String): ValidationResult {
        return when (language.lowercase()) {
            "kotlin" -> validateKotlin(content)
            "java" -> validateJava(content)
            "typescript", "javascript" -> validateJs(content)
            "python" -> validatePython(content)
            else -> ValidationResult(true)
        }
    }
    
    fun validateFile(path: String): ValidationResult {
        val file = File(path)
        if (!file.exists()) {
            return ValidationResult(false, listOf(
                ValidationError(0, 0, "File not found: $path", "FILE_NOT_FOUND")
            ))
        }
        
        val language = when (file.extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "ts", "tsx" -> "typescript"
            "js", "jsx" -> "javascript"
            "py" -> "python"
            else -> return ValidationResult(true)
        }
        
        return try {
            validate(file.readText(), language)
        } catch (e: Exception) {
            ValidationResult(false, listOf(
                ValidationError(0, 0, "Error reading file: ${e.message}", "READ_ERROR")
            ))
        }
    }
    
    private fun validateKotlin(content: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        var lineNum = 0
        
        content.lines().forEach { line ->
            lineNum++
            
            if (line.contains("as Any") || line.contains("as any")) {
                warnings.add(ValidationWarning(lineNum, 0, "Avoid unsafe cast", "UNSAFE_CAST"))
            }
            
            if (line.matches(Regex(".*//\\s*TODO.*"))) {
                warnings.add(ValidationWarning(lineNum, 0, "TODO comment found", "TODO"))
            }
        }
        
        if (content.count { ch -> ch == '{' } != content.count { ch -> ch == '}' }) {
            errors.add(ValidationError(1, 0, "Mismatched braces", "BRACE_MISMATCH"))
        }
        
        if (content.count { ch -> ch == '(' } != content.count { ch -> ch == ')' }) {
            errors.add(ValidationError(1, 0, "Mismatched parentheses", "PAREN_MISMATCH"))
        }
        
        return ValidationResult(errors.isEmpty(), errors, warnings)
    }
    
    private fun validateJava(content: String): ValidationResult {
        return validateKotlin(content)
    }
    
    private fun validateJs(content: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        var lineNum = 0
        
        content.lines().forEach { line ->
            lineNum++
            
            if (line.contains("==") && !line.contains("===")) {
                errors.add(ValidationError(lineNum, 0, "Use === instead of ==", "EQ_EQ"))
            }
            
            if (line.contains("var ") && !line.contains("let ") && !line.contains("const ")) {
                errors.add(ValidationError(lineNum, 0, "Use let or const instead of var", "USE_CONST"))
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    private fun validatePython(content: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        var lineNum = 0
        
        content.lines().forEach { line ->
            lineNum++
            
            if (line.contains("\t") && !line.startsWith("#")) {
                errors.add(ValidationError(lineNum, 0, "Use spaces instead of tabs", "TAB"))
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
}

object LintRules {
    data class Rule(
        val id: String,
        val name: String,
        val description: String,
        val severity: Severity
    )
    
    enum class Severity { ERROR, WARNING, INFO }
    
    val allRules = listOf(
        Rule("UNSAFE_CAST", "Unsafe Cast", "Avoid using unsafe casts", Severity.WARNING),
        Rule("EQ_EQ", "Double Equals", "Use === instead of ==", Severity.ERROR),
        Rule("VAR_DECL", "Var Declaration", "Use let or const instead of var", Severity.WARNING),
        Rule("TAB_INDENT", "Tab Indent", "Use spaces instead of tabs", Severity.WARNING),
        Rule("TODO", "TODO Comment", "TODO comments should be addressed", Severity.INFO),
        Rule("BRACE_MISMATCH", "Brace Mismatch", "Mismatched opening/closing braces", Severity.ERROR),
        Rule("PRINT_STATEMENT", "Print Statement", "Use logging instead of print", Severity.INFO)
    )
    
    fun getRule(id: String): Rule? = allRules.find { it.id == id }
}