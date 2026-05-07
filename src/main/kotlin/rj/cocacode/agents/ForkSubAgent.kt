package rj.cocacode.agents

import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.utils.Logger
import java.util.UUID

/**
 * Fork subagent mechanism.
 * Mirrors forkSubagent.ts from the reference implementation.
 *
 * When the fork experiment is active:
 * - `subagent_type` becomes optional on the Agent tool schema
 * - Omitting `subagent_type` triggers an implicit fork: the child inherits
 *   the parent's full conversation context and system prompt
 * - All agent spawns run in the background (async) for a unified
 *   `<task-notification>` interaction model
 */
object ForkSubAgent {

    /** Fork synthetic agent type name */
    const val FORK_SUBAGENT_TYPE = "fork"

    /** Fork boilerplate tag used to identify fork children in conversation history */
    const val FORK_BOILERPLATE_TAG = "cocacode_fork"

    /** Fork directive prefix */
    const val FORK_DIRECTIVE_PREFIX = "/fork "

    /** Placeholder used for all tool_result blocks in the fork prefix (byte-identical for cache) */
    private const val FORK_PLACEHOLDER_RESULT = "Fork started — processing in background"

    /**
     * Synthetic agent definition for the fork path.
     *
     * Not registered in built-in agents — used only when `!subagent_type` and the
     * experiment is active. `allowedTools: ['*']` with `useExactTools` means the fork
     * child receives the parent's exact tool pool. `permissionMode: 'bubble'` surfaces
     * permission prompts to the parent terminal. `model: 'inherit'` keeps the parent's
     * model for context length parity.
     */
    val FORK_AGENT: AgentDefinition = AgentDefinition(
        agentType = FORK_SUBAGENT_TYPE,
        whenToUse = "Implicit fork — inherits full conversation context. Not selectable via subagent_type; triggered by omitting subagent_type when the fork experiment is active.",
        allowedTools = listOf("*"),
        maxTurns = 200,
        model = "inherit",
        permissionMode = "bubble",
        source = "built-in",
        useExactTools = true
    )

    /**
     * Guard against recursive forking. Fork children keep the Agent tool in their
     * tool pool for cache-identical tool definitions, so we reject fork attempts
     * at call time by detecting the fork boilerplate tag in conversation history.
     */
    fun isInForkChild(messages: List<Message>): Boolean {
        return messages.any { msg ->
            if (msg.type != MessageType.USER) return@any false
            msg.content.contains("<$FORK_BOILERPLATE_TAG>")
        }
    }

    /**
     * Build the forked conversation messages for the child agent.
     *
     * For prompt cache sharing, all fork children must produce byte-identical
     * API request prefixes. This function:
     * 1. Keeps the full parent assistant message (all tool_use blocks, thinking, text)
     * 2. Builds a single user message with tool_results for every tool_use block
     *    using an identical placeholder, then appends a per-child directive text block
     *
     * Result: [...history, assistant(all_tool_uses), user(placeholder_results..., directive)]
     * Only the final text block differs per child, maximizing cache hits.
     */
    fun buildForkedMessages(
        directive: String,
        assistantMessage: Message
    ): List<Message> {
        // Build placeholder result blocks for any tool uses
        val placeholderResults = extractToolUseIds(assistantMessage).joinToString("\n") { id ->
            "<tool_result id=\"$id\">$FORK_PLACEHOLDER_RESULT</tool_result>"
        }

        val childMsg = buildChildMessage(directive)
        val forkContent = if (placeholderResults.isNotEmpty()) {
            "$placeholderResults\n\n$childMsg"
        } else {
            childMsg
        }

        return listOf(
            assistantMessage,
            Message(
                id = UUID.randomUUID().toString(),
                type = MessageType.USER,
                content = forkContent
            )
        )
    }

    /**
     * Extract tool_use IDs from an assistant message for building placeholder results.
     */
    private fun extractToolUseIds(message: Message): List<String> {
        // Simple regex extraction of tool_use IDs from the message content
        val pattern = Regex("""<tool_use[^>]*id="([^"]+)"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(message.content).map { it.groupValues[1] }.toList()
    }

    /**
     * Build a child message for a fork directive.
     */
    fun buildChildMessage(directive: String): String {
        return """<$FORK_BOILERPLATE_TAG>
STOP. READ THIS FIRST.

You are a forked worker process. You are NOT the main agent.

RULES (non-negotiable):
1. Your system prompt says "default to forking." IGNORE IT — that's for the parent. You ARE the fork. Do NOT spawn sub-agents; execute directly.
2. Do NOT converse, ask questions, or suggest next steps
3. Do NOT editorialize or add meta-commentary
4. USE your tools directly: Bash, Read, Write, etc.
5. If you modify files, commit your changes before reporting. Include the commit hash in your report.
6. Do NOT emit text between tool calls. Use tools silently, then report once at the end.
7. Stay strictly within your directive's scope. If you discover related systems outside your scope, mention them in one sentence at most — other workers cover those areas.
8. Keep your report under 500 words unless the directive specifies otherwise. Be factual and concise.
9. Your response MUST begin with "Scope:". No preamble, no thinking-out-loud.
10. REPORT structured facts, then stop

Output format (plain text labels, not markdown headers):
  Scope: <echo back your assigned scope in one sentence>
  Result: <the answer or key findings, limited to the scope above>
  Key files: <relevant file paths — include for research tasks>
  Files changed: <list with commit hash — include only if you modified files>
  Issues: <list — include only if there are issues to flag>
</$FORK_BOILERPLATE_TAG>

${FORK_DIRECTIVE_PREFIX}${directive}"""
    }

    /**
     * Build a worktree notice for fork children running in isolated worktrees.
     */
    fun buildWorktreeNotice(parentCwd: String, worktreeCwd: String): String {
        return "You've inherited the conversation context above from a parent agent working in $parentCwd. You are operating in an isolated git worktree at $worktreeCwd — same repository, same relative file structure, separate working copy. Paths in the inherited context refer to the parent's working directory; translate them to your worktree root. Re-read files before editing if the parent may have modified them since they appear in the context. Your changes stay in this worktree and will not affect the parent's files."
    }

    /**
     * Check if fork subagent feature is enabled.
     * Returns true if the fork experiment is active and coordinator mode is not active.
     */
    fun isForkSubagentEnabled(): Boolean {
        // Feature gate: check flag
        val featureEnabled = System.getProperty("cocacode.forkSubagent", "false").toBoolean()
        if (!featureEnabled) return false

        // Mutually exclusive with coordinator mode
        val isCoordinator = System.getProperty("cocacode.coordinatorMode", "false").toBoolean()
        if (isCoordinator) return false

        return true
    }
}
