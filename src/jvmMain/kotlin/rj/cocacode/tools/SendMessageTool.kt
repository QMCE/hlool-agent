package rj.cocacode.tools

import rj.cocacode.agents.TeammateManager

/**
 * SendMessageTool - lets the main agent send a message to a running teammate.
 *
 * Mirrors SendMessageTool.ts in the reference. The teammate picks the message
 * up from its mailbox and runs again to process it.
 */
class SendMessageTool : Tool(
    name = "SendMessage",
    description = "Send a message to a running teammate. Teammates run in the background and will process your message; use this to delegate follow-up work or request results."
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val to = input["to"] as? String
            ?: return ToolResult(text = "Error: 'to' is required (teammate name)", isError = true)
        val message = input["message"] as? String
            ?: return ToolResult(text = "Error: 'message' is required", isError = true)

        val teammate = TeammateManager.get(to)
            ?: return ToolResult(
                text = "No teammate named '$to'. Active teammates: ${TeammateManager.list().joinToString(", ") { it.name }}",
                isError = true
            )

        TeammateManager.send(to, message)
        return ToolResult(
            text = "Message sent to teammate '$to' (${teammate.queuedCount} queued). " +
                "The teammate will process it and can be checked via its output.",
            metadata = mapOf("to" to to, "queued" to teammate.queuedCount)
        )
    }
}
