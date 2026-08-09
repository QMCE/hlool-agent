package rj.cocacode.agents

import rj.cocacode.types.Message

/**
 * Agent definition for configuring subagent behavior.
 *
 * Mirrors the reference implementation's AgentDefinition from loadAgentsDir.ts.
 */
data class AgentDefinition(
    /** Unique type name (e.g. "general-purpose", "explore") */
    val agentType: String,

    /** Human-readable description of when to use this agent */
    val whenToUse: String,

    /** Static system prompt (alternative to getSystemPrompt) */
    val systemPrompt: String = "",

    /** Dynamic system prompt builder that can include memory, tool descriptions, etc. */
    val getSystemPrompt: (suspend (SubAgentContext) -> String)? = null,

    /**
     * Allowed tools. ["*"] means all available tools.
     * Tool specs can include permission patterns.
     */
    val allowedTools: List<String> = listOf("*"),

    /** Explicitly disallowed tool names */
    val disallowedTools: List<String> = emptyList(),

    /** Maximum conversation turns for this agent */
    val maxTurns: Int = 25,

    /**
     * Model override. null/default = use parent model.
     * "inherit" also inherits parent model.
     */
    val model: String? = null,

    /**
     * Permission mode for tool execution.
     * - null / "" : default permission handling
     * - "bubble" : permission prompts bubble up to parent terminal
     * - "acceptEdits" : auto-accept file edits
     * - "plan" : plan-only mode
     */
    val permissionMode: String? = null,

    /** Source of this agent definition: "built-in" or custom path */
    val source: String = "built-in",

    /** Base directory for custom disk-loaded agents */
    val baseDir: String? = null,

    /** Memory scope for persisted agent memories */
    val memoryScope: String = "none",

    /** Whether this agent runs asynchronously (background) */
    val isAsync: Boolean = false,

    /** Whether to pass the parent's exact tool pool (for cache-identical API prefixes) */
    val useExactTools: Boolean = false,

    /** Required MCP server names for this agent to function */
    val requiredMcpServers: List<String> = emptyList(),

    /** Display color (for UI rendering) */
    val color: String? = null,

    /** Inference temperature */
    val temperature: Float? = null,

    /** Thinking/verbosity level */
    val thinkingLevel: String? = null
)

/**
 * Result from a subagent execution.
 */
data class SubAgentResult(
    val agentType: String,
    val task: String,
    val output: String,
    val messages: List<Message>,
    val isError: Boolean = false,
    val turnCount: Int = 0,
    val toolCallCount: Int = 0,
    val isAsync: Boolean = false,
    val taskNotificationId: String? = null,
    val memoryUpdated: Boolean = false
)

/**
 * Context passed to subagents during execution.
 */
data class SubAgentContext(
    val definition: AgentDefinition,
    val task: String,
    val parentMessages: List<Message>,
    val parentSystemPrompt: String? = null,
    val workingDirectory: String = System.getProperty("user.dir") ?: ".",
    val additionalContext: Map<String, String> = emptyMap(),
    val memoryScope: String = "none",
    /**
     * When true, the engine adds a teammate-role system notice.
     * Conversation continuity is separate: non-empty [parentMessages] are
     * always seeded into the child history (teammate follow-ups, fork, etc.).
     */
    val isTeammate: Boolean = false,
    val useExactTools: Boolean = false,
    val modelOverride: String? = null
)

/**
 * Constants for agent tool names and types.
 * Mirrors constants.ts in reference implementation.
 */
object AgentConstants {
    const val AGENT_TOOL_NAME = "Task"
    const val LEGACY_AGENT_TOOL_NAME = "Agent"

    /** Agent types that always execute in a single turn (no conversation loop) */
    val ONE_SHOT_BUILTIN_AGENT_TYPES = setOf("verification", "plan")

    /** The verification agent type name */
    const val VERIFICATION_AGENT_TYPE = "verification"

    /** Fork synthetic agent type name */
    const val FORK_SUBAGENT_TYPE = "fork"

    /** Default max turns for agents */
    const val DEFAULT_MAX_TURNS = 25
}
