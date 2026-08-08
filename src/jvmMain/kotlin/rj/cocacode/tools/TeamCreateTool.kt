package rj.cocacode.tools

import rj.cocacode.agents.TeammateManager

/**
 * TeamCreateTool - globally control whether newly-spawned subagents become
 * teammates.
 *
 * The main agent acts as the LEADER of a team. When teammates mode is on,
 * subagents spawned with a `name` become persistent teammates: they run in the
 * background and can be addressed later via SendMessage. When off, subagents
 * are one-shot (run and return a result).
 *
 * Mirrors TeamCreateTool.ts in the reference, simplified: no team-name concept.
 */
class TeamCreateTool : Tool(
    name = "TeamCreate",
    description = """
Control teammates mode. As the leader of a team of agents, use this to decide
whether newly-spawned subagents become persistent teammates.

When enabled, spawning a subagent with a `name` creates a teammate that runs in
the background and can receive follow-up messages via SendMessage. Prefer this
for large, independent pieces of work.

operation:
- "on" / "enable"  : enable teammates mode (default)
- "off" / "disable": disable — subagents become one-shot instead
- "status"         : report the current mode
    """.trimIndent()
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val operation = (input["operation"] as? String)?.lowercase() ?: "status"

        return when (operation) {
            "on", "enable" -> {
                TeammateManager.teammatesEnabled = true
                ToolResult(
                    text = "Teammates mode enabled. Subagents spawned with a `name` will become persistent teammates you can message via SendMessage.",
                    metadata = mapOf("operation" to operation, "teammatesEnabled" to true)
                )
            }
            "off", "disable" -> {
                TeammateManager.teammatesEnabled = false
                ToolResult(
                    text = "Teammates mode disabled. New subagents are one-shot (run and return a result).",
                    metadata = mapOf("operation" to operation, "teammatesEnabled" to false)
                )
            }
            "status" -> ToolResult(
                text = if (TeammateManager.teammatesEnabled) {
                    "Teammates mode is ENABLED. ${TeammateManager.activeCount()} active teammate(s): " +
                        TeammateManager.list().joinToString(", ") { it.name }
                } else {
                    "Teammates mode is DISABLED."
                },
                metadata = mapOf(
                    "operation" to operation,
                    "teammatesEnabled" to TeammateManager.teammatesEnabled,
                    "activeTeammates" to TeammateManager.activeCount()
                )
            )
            else -> ToolResult(
                text = "Unknown operation '$operation'. Use on / off / status.",
                isError = true
            )
        }
    }
}
