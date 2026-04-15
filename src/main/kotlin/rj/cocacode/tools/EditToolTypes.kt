package rj.cocacode.tools

import kotlinx.serialization.Serializable

/**
 * Input schema for EditTool
 * Migrated from Claude Code's FileEditTool/types.ts
 */
object EditToolInputSchema {
    val schema = mapOf(
        "file_path" to mapOf(
            "type" to "string",
            "description" to "The absolute path to the file to modify"
        ),
        "old_string" to mapOf(
            "type" to "string",
            "description" to "The text to replace"
        ),
        "new_string" to mapOf(
            "type" to "string",
            "description" to "The text to replace it with (must be different from old_string)"
        ),
        "replace_all" to mapOf(
            "type" to "boolean",
            "description" to "Replace all occurrences of old_string (default false)",
            "default" to false
        )
    )
}

/**
 * Output schema for EditTool
 */
object EditToolOutputSchema {
    val schema = mapOf(
        "filePath" to mapOf("type" to "string", "description" to "The file path that was edited"),
        "oldString" to mapOf("type" to "string", "description" to "The original string that was replaced"),
        "newString" to mapOf("type" to "string", "description" to "The new string that replaced it"),
        "originalFile" to mapOf("type" to "string", "description" to "The original file contents before editing"),
        "structuredPatch" to mapOf("type" to "array", "description" to "Diff patch showing the changes"),
        "userModified" to mapOf("type" to "boolean", "description" to "Whether the user modified the proposed changes"),
        "replaceAll" to mapOf("type" to "boolean", "description" to "Whether all occurrences were replaced")
    )
}

/**
 * Input data class for EditTool
 */
data class EditInput(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

/**
 * Output data class for EditTool
 */
data class EditOutput(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val originalFile: String,
    val structuredPatch: List<PatchHunk> = emptyList(),
    val userModified: Boolean = false,
    val replaceAll: Boolean = false
)

/**
 * Patch hunk representation
 * Corresponds to the diff library's StructuredPatchHunk
 */
@Serializable
data class PatchHunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>
)

/**
 * Individual edit without file_path
 * Used for batch operations
 */
data class Edit(
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

/**
 * File edit validation result
 */
sealed class EditValidationResult {
    data object Valid : EditValidationResult()
    
    data class Invalid(
        val message: String,
        val errorCode: Int,
        val behavior: String = "ask"
    ) : EditValidationResult()
}

/**
 * Constants from Claude Code's FileEditTool/constants.ts
 */
object EditToolConstants {
    const val TOOL_NAME = "Edit"
    const val TOOL_DESCRIPTION = "A tool for editing files"
    
    const val FILE_UNEXPECTEDLY_MODIFIED_ERROR = 
        "File has been unexpectedly modified. Read it again before attempting to write it."
    
    const val FILE_NOT_FOUND_NOTE = "File does not exist."
    
    // Maximum editable file size (1 GiB)
    const val MAX_EDIT_FILE_SIZE = 1024L * 1024L * 1024L
    
    // Quote normalization constants
    const val LEFT_SINGLE_CURLY_QUOTE = '\u2018'
    const val RIGHT_SINGLE_CURLY_QUOTE = '\u2019'
    const val LEFT_DOUBLE_CURLY_QUOTE = '\u201C'
    const val RIGHT_DOUBLE_CURLY_QUOTE = '\u201D'
}

/**
 * GlobTool Constants
 * Migrated from Claude Code's GlobTool/GlobTool.ts
 */
object GlobToolConstants {
    const val TOOL_NAME = "Glob"
    const val TOOL_DESCRIPTION = "A tool for finding files by glob pattern"
    
    const val DEFAULT_MAX_RESULTS = 100
    const val MAX_RESULT_SIZE_CHARS = 100_000
}

/**
 * Input schema for GlobTool
 * Migrated from Claude Code's GlobTool
 */
object GlobToolInputSchema {
    val schema = mapOf(
        "pattern" to mapOf(
            "type" to "string",
            "description" to "The glob pattern to match files against"
        ),
        "path" to mapOf(
            "type" to "string",
            "description" to "The directory to search in. If not specified, the current working directory will be used."
        )
    )
}

/**
 * Output schema for GlobTool
 */
object GlobToolOutputSchema {
    val schema = mapOf(
        "durationMs" to mapOf("type" to "number", "description" to "Time taken to execute the search in milliseconds"),
        "numFiles" to mapOf("type" to "number", "description" to "Total number of files found"),
        "filenames" to mapOf("type" to "array", "description" to "Array of file paths that match the pattern"),
        "truncated" to mapOf("type" to "boolean", "description" to "Whether results were truncated (limited to 100 files)")
    )
}

/**
 * Output data class for GlobTool
 */
data class GlobOutput(
    val durationMs: Long,
    val numFiles: Int,
    val filenames: List<String>,
    val truncated: Boolean
)