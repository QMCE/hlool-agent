package rj.cocacode.services.api

import rj.cocacode.tools.ToolRegistry

/**
 * Maps each registered tool to a JSON Schema used as `input_schema` (Anthropic)
 * or `parameters` (OpenAI) in the tool definitions sent to the model.
 *
 * The concrete tool implementations parse their input as a flat
 * `Map<String, Any>` keyed by these names; the schemas describe exactly those
 * keys so the model can construct valid arguments.
 */
object ToolSchemas {

    fun specsForRegisteredTools(): List<ToolSpec> =
        ToolRegistry.all().map { tool ->
            ToolSpec(
                name = tool.name,
                description = tool.description,
                inputSchema = schemaForTool(tool.name)
            )
        }

    /** JSON Schema for a single tool name. */
    fun schemaForTool(name: String): Map<String, Any> = schemaFor(name)

    private fun schemaFor(name: String): Map<String, Any> = when (name) {
        "Bash" -> obj(
            props = mapOf(
                "command" to str("The shell command to execute"),
                "description" to str("Human-readable description of what the command does"),
                "timeout" to int("Timeout in milliseconds"),
                "workdir" to str("Working directory for the command"),
                "run_in_background" to bool("Run the command in the background"),
                "env" to mapOf("type" to "object", "description" to "Extra environment variables")
            ),
            required = listOf("command")
        )

        "Read" -> obj(
            props = mapOf(
                "file_path" to str("Absolute path to the file to read"),
                "offset" to int("1-indexed starting line"),
                "limit" to int("Maximum number of lines to read")
            ),
            required = listOf("file_path")
        )

        "Write" -> obj(
            props = mapOf(
                "file_path" to str("Absolute path to the file to write"),
                "content" to str("The content to write")
            ),
            required = listOf("file_path", "content")
        )

        "Edit" -> obj(
            props = mapOf(
                "file_path" to str("Absolute path to the file to edit"),
                "old_string" to str("The text to replace"),
                "new_string" to str("The replacement text"),
                "replace_all" to bool("Replace all occurrences")
            ),
            required = listOf("file_path", "old_string", "new_string")
        )

        "Glob" -> obj(
            props = mapOf(
                "pattern" to str("The glob pattern to match files against"),
                "path" to str("Directory to search in (defaults to cwd)")
            ),
            required = listOf("pattern")
        )

        "Grep" -> obj(
            props = mapOf(
                "pattern" to str("The regex pattern to search for"),
                "path" to str("Path to search in"),
                "glob" to str("File glob filter"),
                "output_mode" to mapOf(
                    "type" to "string",
                    "enum" to listOf("content", "count", "files_with_matches"),
                    "description" to "How to render results"
                ),
                "context" to int("Lines of context"),
                "head_limit" to int("Max results"),
                "offset" to int("Starting offset"),
                "multiline" to bool("Match across lines"),
                "type" to str("File type filter")
            ),
            required = listOf("pattern")
        )

        "TaskCreate" -> obj(
            props = mapOf(
                "subject" to str("A brief title for the task"),
                "description" to str("What needs to be done")
            ),
            required = listOf("subject")
        )

        "TaskList" -> mapOf("type" to "object")

        "TaskOutput" -> obj(
            props = mapOf(
                "task_id" to str("The ID of the task")
            ),
            required = listOf("task_id")
        )

        "TaskStop" -> obj(
            props = mapOf(
                "task_id" to str("The ID of the task to stop")
            ),
            required = listOf("task_id")
        )

        "SendMessage" -> obj(
            props = mapOf(
                "to" to str("The name of the teammate to send the message to"),
                "message" to str("The message content")
            ),
            required = listOf("to", "message")
        )

        "TeamCreate" -> obj(
            props = mapOf(
                "operation" to str("'on' to enable teammates mode, 'off' to disable, 'status' to check")
            ),
            required = emptyList()
        )

        else -> mapOf("type" to "object")
    }

    private fun obj(props: Map<String, Any>, required: List<String>): Map<String, Any> =
        mapOf("type" to "object", "properties" to props, "required" to required)

    private fun str(description: String): Map<String, Any> =
        mapOf("type" to "string", "description" to description)

    private fun int(description: String): Map<String, Any> =
        mapOf("type" to "integer", "description" to description)

    private fun bool(description: String): Map<String, Any> =
        mapOf("type" to "boolean", "description" to description)
}
