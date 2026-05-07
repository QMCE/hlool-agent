package rj.cocacode.tools

import rj.cocacode.utils.FileUtils
import java.io.File

class EditToolImpl : Tool(EditToolConstants.TOOL_NAME, EditToolConstants.TOOL_DESCRIPTION) {
    
    private val undoManager = UndoManager()
    private val readFileTimestamps = mutableMapOf<String, Long>()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val filePath = input["file_path"] as? String 
            ?: return ToolResult(text = "Error: file_path required", isError = true)
        val oldString = input["old_string"] as? String 
            ?: return ToolResult(text = "Error: old_string required", isError = true)
        val newString = input["new_string"] as? String 
            ?: return ToolResult(text = "Error: new_string required", isError = true)
        val replaceAll = input["replace_all"] as? Boolean ?: false
        
        val validationResult = validateInput(filePath, oldString, newString, replaceAll)
        if (validationResult is EditValidationResult.Invalid) {
            return ToolResult(text = validationResult.message, isError = true)
        }
        
        return try {
            val file = File(expandPath(filePath))
            val absolutePath = file.absolutePath
            
            val originalContent = if (file.exists()) {
                readFileContent(absolutePath)
            } else {
                ""
            }
            
            val actualOldString = findActualString(originalContent, oldString) ?: oldString
            val actualNewString = preserveQuoteStyle(oldString, actualOldString, newString)
            
            val newContent = applyEdit(
                originalContent, 
                actualOldString, 
                actualNewString, 
                replaceAll
            )
            
            file.parentFile?.mkdirs()
            file.writeText(newContent)
            
            undoManager.recordEdit(
                absolutePath,
                originalContent,
                newContent,
                mapOf("replaceAll" to replaceAll)
            )
            
            readFileTimestamps[absolutePath] = file.lastModified()
            
            val patch = generatePatch(originalContent, newContent)
            
            val output = EditOutput(
                filePath = absolutePath,
                oldString = actualOldString,
                newString = newString,
                originalFile = originalContent,
                structuredPatch = patch,
                replaceAll = replaceAll
            )
            
            val message = if (replaceAll) {
                "File $absolutePath has been updated. All occurrences were successfully replaced."
            } else {
                "File $absolutePath has been updated successfully."
            }
            
            ToolResult(
                text = message,
                metadata = mapOf(
                    "filePath" to absolutePath,
                    "oldString" to actualOldString,
                    "newString" to newString,
                    "replaceAll" to replaceAll,
                    "patch" to patch.map { it.toString() }
                )
            )
        } catch (e: Exception) {
            ToolResult(
                text = "Error editing file: ${e.message}",
                isError = true
            )
        }
    }
    
    private fun validateInput(
        filePath: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): EditValidationResult {
        if (oldString == newString) {
            return EditValidationResult.Invalid(
                message = "No changes to make: old_string and new_string are exactly the same.",
                errorCode = 1
            )
        }
        
        val absolutePath = expandPath(filePath)
        val file = File(absolutePath)
        
        if (!file.exists()) {
            if (oldString.isEmpty()) {
                return EditValidationResult.Valid
            }
            return EditValidationResult.Invalid(
                message = "File does not exist: $absolutePath",
                errorCode = 4
            )
        }
        
        if (oldString.isEmpty()) {
            return EditValidationResult.Invalid(
                message = "Cannot create new file - file already exists.",
                errorCode = 3
            )
        }
        
        if (absolutePath.endsWith(".ipynb")) {
            return EditValidationResult.Invalid(
                message = "File is a Jupyter Notebook. Use NotebookEditTool to edit this file.",
                errorCode = 5
            )
        }
        
        val lastRead = readFileTimestamps[absolutePath]
        if (lastRead != null) {
            val lastWrite = file.lastModified()
            if (lastWrite > lastRead) {
                return EditValidationResult.Invalid(
                    message = "File has been modified since read. Read it again before attempting to write it.",
                    errorCode = 7
                )
            }
        }
        
        val content = readFileContent(absolutePath)
        if (!content.contains(oldString) && !content.contains(normalizeQuotes(oldString))) {
            return EditValidationResult.Invalid(
                message = "String to replace not found in file.\nString: $oldString",
                errorCode = 8
            )
        }
        
        val matches = content.split(actualOldStringFind(content, oldString)).size - 1
        if (matches > 1 && !replaceAll) {
            return EditValidationResult.Invalid(
                message = "Found $matches matches of the string to replace, but replace_all is false. " +
                    "To replace all occurrences, set replace_all to true.",
                errorCode = 9
            )
        }
        
        return EditValidationResult.Valid
    }
    
    private fun expandPath(path: String): String {
        return File(path).absolutePath
    }
    
    private fun readFileContent(filePath: String): String {
        return File(filePath).readText().replace("\r\n", "\n")
    }
    
    private fun findActualString(fileContent: String, searchString: String): String? {
        if (fileContent.contains(searchString)) {
            return searchString
        }
        val normalized = normalizeQuotes(searchString)
        val normalizedFile = normalizeQuotes(fileContent)
        val index = normalizedFile.indexOf(normalized)
        return if (index >= 0) {
            fileContent.substring(index, index + searchString.length)
        } else {
            null
        }
    }
    
    private fun actualOldStringFind(fileContent: String, searchString: String): String {
        return findActualString(fileContent, searchString) ?: searchString
    }
    
    private fun normalizeQuotes(str: String): String {
        return str
            .replace(EditToolConstants.LEFT_SINGLE_CURLY_QUOTE, '\'')
            .replace(EditToolConstants.RIGHT_SINGLE_CURLY_QUOTE, '\'')
            .replace(EditToolConstants.LEFT_DOUBLE_CURLY_QUOTE, '"')
            .replace(EditToolConstants.RIGHT_DOUBLE_CURLY_QUOTE, '"')
    }
    
    private fun preserveQuoteStyle(
        oldString: String,
        actualOldString: String,
        newString: String
    ): String {
        if (oldString == actualOldString) {
            return newString
        }
        
        val hasDoubleQuotes = actualOldString.contains(EditToolConstants.LEFT_DOUBLE_CURLY_QUOTE) ||
            actualOldString.contains(EditToolConstants.RIGHT_DOUBLE_CURLY_QUOTE)
        val hasSingleQuotes = actualOldString.contains(EditToolConstants.LEFT_SINGLE_CURLY_QUOTE) ||
            actualOldString.contains(EditToolConstants.RIGHT_SINGLE_CURLY_QUOTE)
        
        if (!hasDoubleQuotes && !hasSingleQuotes) {
            return newString
        }
        
        var result = newString
        if (hasDoubleQuotes) {
            result = applyCurlyDoubleQuotes(result)
        }
        if (hasSingleQuotes) {
            result = applyCurlySingleQuotes(result)
        }
        
        return result
    }
    
    private fun isOpeningContext(chars: List<Char>, index: Int): Boolean {
        if (index == 0) return true
        val prev = chars[index - 1]
        return prev == ' ' || prev == '\t' || prev == '\n' || prev == '\r' ||
            prev == '(' || prev == '[' || prev == '{'
    }
    
    private fun applyCurlyDoubleQuotes(str: String): String {
        val chars = str.toList()
        val result = mutableListOf<Char>()
        for (i in chars.indices) {
            if (chars[i] == '"') {
                result.add(
                    if (isOpeningContext(chars, i)) 
                        EditToolConstants.LEFT_DOUBLE_CURLY_QUOTE 
                    else 
                        EditToolConstants.RIGHT_DOUBLE_CURLY_QUOTE
                )
            } else {
                result.add(chars[i])
            }
        }
        return result.joinToString("")
    }
    
    private fun applyCurlySingleQuotes(str: String): String {
        val chars = str.toList()
        val result = mutableListOf<Char>()
        for (i in chars.indices) {
            if (chars[i] == '\'') {
                val prev = if (i > 0) chars[i - 1] else null
                val next = if (i < chars.size - 1) chars[i + 1] else null
                val prevIsLetter = prev != null && prev.isLetter()
                val nextIsLetter = next != null && next.isLetter()
                if (prevIsLetter && nextIsLetter) {
                    result.add(EditToolConstants.RIGHT_SINGLE_CURLY_QUOTE)
                } else {
                    result.add(
                        if (isOpeningContext(chars, i)) 
                            EditToolConstants.LEFT_SINGLE_CURLY_QUOTE 
                        else 
                            EditToolConstants.RIGHT_SINGLE_CURLY_QUOTE
                    )
                }
            } else {
                result.add(chars[i])
            }
        }
        return result.joinToString("")
    }
    
    private fun applyEdit(
        originalContent: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): String {
        return if (replaceAll) {
            originalContent.replace(oldString, newString)
        } else {
            originalContent.replaceFirst(oldString, newString)
        }
    }
    
    private fun generatePatch(oldContent: String, newContent: String): List<PatchHunk> {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        val hunks = mutableListOf<PatchHunk>()
        var i = 0
        var j = 0
        var hunkLines = mutableListOf<String>()
        var hunkStart = i + 1
        
        while (i < oldLines.size || j < newLines.size) {
            val oldLine = oldLines.getOrNull(i)
            val newLine = newLines.getOrNull(j)
            
            when {
                oldLine == newLine -> {
                    hunkLines.add(" $oldLine")
                    i++
                    j++
                }
                oldLine != null && newLine != null && oldLine != newLine -> {
                    hunkLines.add("-$oldLine")
                    hunkLines.add("+$newLine")
                    i++
                    j++
                }
                oldLine != null && newLine == null -> {
                    hunkLines.add("-$oldLine")
                    i++
                }
                oldLine == null && newLine != null -> {
                    hunkLines.add("+$newLine")
                    j++
                }
            }
            
            if (hunkLines.size >= 2) {
                val contextCount = hunkLines.count { it.startsWith(" ") }
                if (contextCount == 0 || i >= oldLines.size || j >= newLines.size) {
                    if (hunkLines.isNotEmpty()) {
                        hunks.add(PatchHunk(
                            oldStart = hunkStart,
                            oldLines = i - hunkStart + 1,
                            newStart = hunkStart,
                            newLines = j - hunkStart + 1,
                            lines = hunkLines.toList()
                        ))
                        hunkLines.clear()
                        hunkStart = i + 1
                    }
                }
            }
        }
        
        if (hunkLines.isNotEmpty()) {
            hunks.add(PatchHunk(
                oldStart = hunkStart,
                oldLines = oldLines.size - hunkStart + 1,
                newStart = hunkStart,
                newLines = newLines.size - hunkStart + 1,
                lines = hunkLines.toList()
            ))
        }
        
        return hunks
    }
    
    fun canUndo(): Boolean = undoManager.canUndo()
    
    fun canRedo(): Boolean = undoManager.canRedo()
    
    fun undo(): ToolResult {
        val entry = undoManager.undo() ?: return ToolResult(
            text = "Nothing to undo",
            isError = true
        )
        
        return try {
            File(entry.filePath).writeText(entry.oldContent)
            ToolResult(text = "Undone: ${entry.filePath}")
        } catch (e: Exception) {
            ToolResult(text = "Undo failed: ${e.message}", isError = true)
        }
    }
    
    fun redo(): ToolResult {
        val entry = undoManager.redo() ?: return ToolResult(
            text = "Nothing to redo",
            isError = true
        )
        
        return try {
            File(entry.filePath).writeText(entry.newContent)
            ToolResult(text = "Redone: ${entry.filePath}")
        } catch (e: Exception) {
            ToolResult(text = "Redo failed: ${e.message}", isError = true)
        }
    }
    
    fun getLastEditInfo(): EditInfo? = undoManager.getLastEditInfo()
}