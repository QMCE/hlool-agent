package rj.cocacode.query

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rj.cocacode.utils.generateUuid
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.types.Attachment
import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.tools.ToolExecutionResult
import rj.cocacode.config.FeatureFlags
import rj.cocacode.config.ApiConfig
import rj.cocacode.state.AppStateManager
import rj.cocacode.state.AppState
import rj.cocacode.utils.ThinkingConfig
import rj.cocacode.utils.TokenEstimator
import rj.cocacode.utils.Logger
import rj.cocacode.utils.Metrics
import rj.cocacode.services.api.ApiClient
import rj.cocacode.services.api.ApiError
import rj.cocacode.services.api.FallbackTriggeredError

// Maximum recovery attempts for max_output_tokens errors
private const val MAX_OUTPUT_TOKENS_RECOVERY_LIMIT = 3

/**
 * Query parameters for the main query loop.
 */
data class QueryParams(
    val messages: List<Message>,
    val systemPrompt: String,
    val userContext: Map<String, String>,
    val systemContext: Map<String, String>,
    val canUseTool: (String) -> Boolean,
    val toolUseContext: QueryToolUseContext,
    val fallbackModel: String? = null,
    val querySource: QuerySource,
    val maxOutputTokensOverride: Int? = null,
    val maxTurns: Int? = null,
    val skipCacheWrite: Boolean = false,
    val taskBudget: TaskBudget? = null,
    val deps: QueryDeps? = null
)

/**
 * Task budget for controlling total token usage across a turn.
 */
data class TaskBudget(
    val total: Int
)

/**
 * Query source types.
 */
enum class QuerySource {
    REPL_MAIN_THREAD,
    SDK,
    SUBAGENT,
    COMPACT,
    SESSION_MEMORY,
    BACKGROUND
}

/**
 * Query tool use context with options and state.
 */
data class QueryToolUseContext(
    val options: QueryToolUseOptions,
    val abortController: AbortController,
    val readFileState: FileStateCache,
    val getAppState: () -> AppState,
    val setAppState: (AppState) -> Unit,
    val messages: List<Message>,
    val setInProgressToolUseIDs: () -> Unit,
    val setResponseLength: (Int) -> Unit,
    val updateFileHistoryState: () -> Unit,
    val updateAttributionState: () -> Unit,
    val agentId: String? = null,
    val queryTracking: QueryTracking? = null,
    val contentReplacementState: ContentReplacementState? = null
)

/**
 * Query tool use options.
 */
data class QueryToolUseOptions(
    val commands: List<String>,
    val debug: Boolean,
    val mainLoopModel: String,
    val tools: List<Tool>,
    val verbose: Boolean,
    val thinkingConfig: ThinkingConfig,
    val mcpClients: List<McpServerConnection>,
    val mcpResources: Map<String, Any>,
    val isNonInteractiveSession: Boolean,
    val agentDefinitions: AgentDefinitions,
    val customSystemPrompt: String?,
    val appendSystemPrompt: String?,
    val refreshTools: (() -> List<Tool>)? = null,
    val addNotification: ((Notification) -> Unit)? = null
)

/**
 * Content replacement state for tool result budget.
 */
data class ContentReplacementState(
    val replacements: Map<String, String> = emptyMap()
)

/**
 * Query tracking for chain ID and depth.
 */
data class QueryTracking(
    val chainId: String,
    val depth: Int
)

/**
 * MCP server connection.
 */
data class McpServerConnection(
    val name: String,
    val status: String,
    val tools: List<String> = emptyList(),
    val resources: List<String> = emptyList()
)

/**
 * Agent definitions.
 */
data class AgentDefinitions(
    val activeAgents: List<AgentDefinition>,
    val allowedAgentTypes: List<String> = emptyList()
)

/**
 * Agent definition.
 */
data class AgentDefinition(
    val name: String,
    val description: String,
    val prompt: String
)

/**
 * Notification for UI updates.
 */
data class Notification(
    val key: String,
    val text: String,
    val priority: String
)

/**
 * Query loop state carried between iterations.
 */
data class QueryLoopState(
    val messages: List<Message>,
    val toolUseContext: QueryToolUseContext,
    val autoCompactTracking: AutoCompactTrackingState?,
    val maxOutputTokensRecoveryCount: Int,
    val hasAttemptedReactiveCompact: Boolean,
    val maxOutputTokensOverride: Int?,
    val pendingToolUseSummary: Deferred<ToolUseSummaryMessage?>?,
    val stopHookActive: Boolean?,
    val turnCount: Int,
    val transition: Continue?
)

/**
 * Auto compact tracking state.
 */
data class AutoCompactTrackingState(
    val compacted: Boolean,
    val turnId: String,
    val turnCounter: Int,
    val consecutiveFailures: Int
)

/**
 * Continue reasons for query loop transitions.
 */
sealed class Continue {
    data class ContinueReason(val reason: String) : Continue()
    data class CollapseDrainRetry(val committed: Int) : Continue()
    data class ReactiveCompactRetry(val reason: String = "reactive_compact_retry") : Continue()
    data class MaxOutputTokensEscalate(val reason: String = "max_output_tokens_escalate") : Continue()
    data class MaxOutputTokensRecovery(val attempt: Int) : Continue()
    data class StopHookBlocking(val reason: String = "stop_hook_blocking") : Continue()
    data class TokenBudgetContinuation(val reason: String = "token_budget_continuation") : Continue()
    data class NextTurn(val reason: String = "next_turn") : Continue()
}

/**
 * Terminal states for query loop.
 */
sealed class Terminal {
    data class Reason(val reason: String, val error: Throwable? = null, val turnCount: Int? = null) : Terminal()
}

/**
 * Stream events from query loop.
 */
sealed class StreamEvent {
    data object StreamRequestStart : StreamEvent()
    data class StreamChunk(val content: String, val delta: String? = null) : StreamEvent()
    data class AssistantMessage(val message: Message) : StreamEvent()
    data class ToolUseMessage(val message: Message) : StreamEvent()
    data class ToolResultMessage(val message: Message) : StreamEvent()
    data class ErrorMessage(val content: String, val error: String?) : StreamEvent()
    data class TombstoneMessage(val message: Message) : StreamEvent()
    data class CompactBoundaryMessage(val type: String, val tokensFreed: Int) : StreamEvent()
    data class ToolUseSummaryMessage(val summary: String, val toolUseIds: List<String>) : StreamEvent()
}

/**
 * Tool use summary message.
 */
data class ToolUseSummaryMessage(
    val summary: String,
    val toolUseIds: List<String>
)

/**
 * Request start event.
 */
data class RequestStartEvent(
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Check if message is a withheld max_output_tokens error.
 */
fun isWithheldMaxOutputTokens(msg: Any?): Boolean {
    if (msg is Message) {
        return msg.type == MessageType.ASSISTANT && msg.apiError == "max_output_tokens"
    }
    return false
}

/**
 * Check if message is a prompt-too-long error.
 */
fun isPromptTooLongMessage(msg: Message): Boolean {
    return msg.apiError == "prompt_too_long" || msg.content.contains("prompt too long", ignoreCase = true)
}

/**
 * Check if message is an API error.
 */
fun Message.isApiErrorMessage(): Boolean {
    return this.apiError != null
}

/**
 * Extension property for API error on Message.
 */
var Message.apiError: String?
    get() = null
    set(_) {}

/**
 * Yield missing tool result blocks for interrupted tools.
 */
fun yieldMissingToolResultBlocks(
    assistantMessages: List<Message>,
    errorMessage: String
): Flow<Message> = flow {
    for (assistantMessage in assistantMessages) {
        val toolUseBlocks = assistantMessage.contentBlocks.filter { it.type == "tool_use" }
        for (toolUse in toolUseBlocks) {
            val resultMessage = Message(
                id = generateUuid(),
                type = MessageType.USER,
                content = errorMessage,
                isError = true,
                toolUseId = toolUse.id
            )
            emit(resultMessage)
        }
    }
}

/**
 * Main query function - async generator for multi-turn dialogue.
 * 
 * This is the core engine of the conversational AI system, handling:
 * - Multi-turn dialogue with tool execution
 * - Error recovery (prompt-too-long, max_output_tokens, model fallback)
 * - Context compaction (autocompact, reactive compact, microcompact)
 * - Token budget checking and auto-continue
 * - Post-sampling hook execution
 * - Memory and skill prefetch
 */
suspend fun query(params: QueryParams): Flow<Any> = flow {
    val consumedCommandUuids = mutableListOf<String>()
    
    // Run the main query loop
    val terminal = queryLoop(params, consumedCommandUuids)
    
    terminal.collect { event ->
        emit(event)
    }
    
    // Notify command lifecycle for consumed commands
    for (uuid in consumedCommandUuids) {
        notifyCommandLifecycle(uuid, "completed")
    }
}

/**
 * Main query loop - handles multi-turn conversation with tool execution.
 */
suspend fun queryLoop(
    params: QueryParams,
    consumedCommandUuids: MutableList<String>
): Flow<Any> = flow {
    // Immutable params - never reassigned during the query loop
    val systemPrompt = params.systemPrompt
    val userContext = params.userContext
    val systemContext = params.systemContext
    val canUseTool = params.canUseTool
    val fallbackModel = params.fallbackModel
    val querySource = params.querySource
    val maxTurns = params.maxTurns
    val skipCacheWrite = params.skipCacheWrite
    val deps = params.deps ?: productionDeps()
    
    // Mutable cross-iteration state
    var state = QueryLoopState(
        messages = params.messages,
        toolUseContext = params.toolUseContext,
        maxOutputTokensOverride = params.maxOutputTokensOverride,
        autoCompactTracking = null,
        stopHookActive = null,
        maxOutputTokensRecoveryCount = 0,
        hasAttemptedReactiveCompact = false,
        turnCount = 1,
        pendingToolUseSummary = null,
        transition = null
    )
    
    val budgetTracker = if (FeatureFlags.isEnabled("TOKEN_BUDGET")) {
        createBudgetTracker()
    } else null
    
    // Task budget remaining tracking across compaction boundaries
    var taskBudgetRemaining: Int? = null
    
    // Build query config once at entry
    val config = buildQueryConfig()
    
    // Memory prefetch - fired once per user turn
    val pendingMemoryPrefetch = startMemoryPrefetch(state.messages, state.toolUseContext)
    
    // Main query loop - runs until terminal state
    while (true) {
        // Destructure state at the top of each iteration
        var toolUseContext = state.toolUseContext
        val messages = state.messages
        val autoCompactTracking = state.autoCompactTracking
        val maxOutputTokensRecoveryCount = state.maxOutputTokensRecoveryCount
        val hasAttemptedReactiveCompact = state.hasAttemptedReactiveCompact
        val maxOutputTokensOverride = state.maxOutputTokensOverride
        val pendingToolUseSummary = state.pendingToolUseSummary
        val stopHookActive = state.stopHookActive
        val turnCount = state.turnCount
        
        // Skill discovery prefetch - per iteration
        val pendingSkillPrefetch = startSkillDiscoveryPrefetch(messages, toolUseContext)
        
        emit(StreamEvent.StreamRequestStart)
        
        // Initialize or increment query chain tracking
        val queryTracking = if (toolUseContext.queryTracking != null) {
            QueryTracking(
                chainId = toolUseContext.queryTracking.chainId,
                depth = toolUseContext.queryTracking.depth + 1
            )
        } else {
            QueryTracking(
                chainId = deps.uuid(),
                depth = 0
            )
        }
        
        toolUseContext = toolUseContext.copy(queryTracking = queryTracking)
        
        // Get messages after compact boundary
        var messagesForQuery = getMessagesAfterCompactBoundary(messages)
        
        // Apply tool result budget
        messagesForQuery = applyToolResultBudget(
            messagesForQuery,
            toolUseContext.contentReplacementState,
            querySource
        )
        
        // Apply snip compact if enabled
        var snipTokensFreed = 0
        if (FeatureFlags.isEnabled("HISTORY_SNIP")) {
            val snipResult = applySnipCompact(messagesForQuery)
            messagesForQuery = snipResult.messages
            snipTokensFreed = snipResult.tokensFreed
            snipResult.boundaryMessage?.let { emit(it) }
        }
        
        // Apply microcompact before autocompact
        val microcompactResult = deps.microcompact.invoke(
            MicroCompactInput(messagesForQuery, toolUseContext, querySource)
        )
        messagesForQuery = microcompactResult.messages
        
        // Apply context collapse if enabled
        if (FeatureFlags.isEnabled("CONTEXT_COLLAPSE")) {
            val collapseResult = applyContextCollapse(messagesForQuery, toolUseContext, querySource)
            messagesForQuery = collapseResult
        }
        
        val fullSystemPrompt = systemPrompt // Could append systemContext here
        
        // Run autocompact
        val (compactionResult, consecutiveFailures) = deps.autocompact.invoke(
            AutoCompactInput(
                messages = messagesForQuery,
                toolUseContext = toolUseContext,
                systemPrompt = systemPrompt,
                userContext = userContext,
                systemContext = systemContext,
                forkContextMessages = messagesForQuery
            ),
            querySource,
            autoCompactTracking,
            snipTokensFreed
        )
        
        if (compactionResult != null) {
            // Log compaction success event
            logEvent("tengu_auto_compact_succeeded", mapOf(
                "originalMessageCount" to messages.size,
                "compactedMessageCount" to (compactionResult.summaryMessages.size + 
                    compactionResult.attachments.size + compactionResult.hookResults.size),
                "preCompactTokenCount" to compactionResult.preCompactTokenCount,
                "postCompactTokenCount" to compactionResult.postCompactTokenCount
            ))
            
            // Task budget: capture pre-compact final context window
            if (params.taskBudget != null) {
                val preCompactContext = getFinalContextTokens(messagesForQuery)
                taskBudgetRemaining = maxOf(0, (taskBudgetRemaining ?: params.taskBudget.total) - preCompactContext)
            }
            
            // Reset tracking on every compact
            val tracking = AutoCompactTrackingState(
                compacted = true,
                turnId = deps.uuid(),
                turnCounter = 0,
                consecutiveFailures = 0
            )
            
            // Yield post-compact messages
            val postCompactMessages = buildPostCompactMessages(compactionResult)
            for (message in postCompactMessages) {
                emit(message)
            }
            
            messagesForQuery = postCompactMessages
        } else if (consecutiveFailures != null) {
            // Autocompact failed - propagate failure count
            val tracking = autoCompactTracking?.copy(consecutiveFailures = consecutiveFailures)
                ?: AutoCompactTrackingState(compacted = false, turnId = "", turnCounter = 0, consecutiveFailures = consecutiveFailures)
        }
        
        // Update tool use context with messages
        toolUseContext = toolUseContext.copy(messages = messagesForQuery)
        
        // Prepare for API call
        val assistantMessages = mutableListOf<Message>()
        val toolResults = mutableListOf<Message>()
        val toolUseBlocks = mutableListOf<ToolUseBlock>()
        var needsFollowUp = false
        
        // Get streaming tool execution setting
        val useStreamingToolExecution = config.gates.streamingToolExecution
        val streamingToolExecutor = if (useStreamingToolExecution) {
            StreamingToolExecutor(toolUseContext.options.tools, canUseTool, toolUseContext)
        } else null
        
        val appState = toolUseContext.getAppState()
        val permissionMode = appState.toolPermissionContext?.mode ?: "auto"
        var currentModel = getRuntimeMainLoopModel(
            permissionMode = permissionMode,
            mainLoopModel = toolUseContext.options.mainLoopModel,
            exceeds200kTokens = permissionMode == "plan" && doesMostRecentAssistantMessageExceed200k(messagesForQuery)
        )
        
        // Check for blocking limit (only when auto-compact is OFF)
        val collapseOwnsIt = FeatureFlags.isEnabled("CONTEXT_COLLAPSE") && isAutoCompactEnabled()
        if (!compactionResult && 
            querySource != QuerySource.COMPACT && 
            querySource != QuerySource.SESSION_MEMORY &&
            !isReactiveCompactEnabled() &&
            !collapseOwnsIt) {
            val tokenCount = estimateTokenCount(messagesForQuery) - snipTokensFreed
            val isAtBlockingLimit = isAtBlockingLimit(tokenCount, toolUseContext.options.mainLoopModel)
            if (isAtBlockingLimit) {
                emit(createApiErrorMessage("Prompt too long - conversation exceeds context limit", "invalid_request"))
                return@flow
            }
        }
        
        var attemptWithFallback = true
        
        // API streaming loop
        while (attemptWithFallback) {
            attemptWithFallback = false
            try {
                var streamingFallbackOccurred = false
                
                // Call model with streaming
                val stream = deps.callModel.invoke(
                    CallModelInput(
                        messages = prependUserContext(messagesForQuery, userContext),
                        systemPrompt = fullSystemPrompt,
                        thinkingConfig = toolUseContext.options.thinkingConfig,
                        tools = toolUseContext.options.tools,
                        signal = toolUseContext.abortController,
                        options = CallModelOptions(
                            model = currentModel,
                            fallbackModel = fallbackModel,
                            onStreamingFallback = { streamingFallbackOccurred = true },
                            querySource = querySource.name,
                            agents = toolUseContext.options.agentDefinitions.activeAgents,
                            allowedAgentTypes = toolUseContext.options.agentDefinitions.allowedAgentTypes,
                            hasAppendSystemPrompt = toolUseContext.options.appendSystemPrompt != null,
                            maxOutputTokensOverride = maxOutputTokensOverride,
                            agentId = toolUseContext.agentId,
                            addNotification = toolUseContext.options.addNotification,
                            taskBudget = params.taskBudget?.let { tb ->
                                TaskBudgetInput(tb.total, taskBudgetRemaining)
                            }
                        )
                    )
                )
                
                stream.collect { message ->
                    // Handle streaming fallback
                    if (streamingFallbackOccurred) {
                        // Yield tombstones for orphaned messages
                        for (msg in assistantMessages) {
                            emit(StreamEvent.TombstoneMessage(msg))
                        }
                        logEvent("tengu_orphaned_messages_tombstoned", mapOf(
                            "orphanedMessageCount" to assistantMessages.size
                        ))
                        
                        assistantMessages.clear()
                        toolResults.clear()
                        toolUseBlocks.clear()
                        needsFollowUp = false
                        
                        // Discard pending results from failed streaming attempt
                        streamingToolExecutor?.discard()
                    }
                    
                    // Withhold recoverable errors
                    var withheld = false
                    if (isReactiveCompactEnabled()) {
                        val rc = getReactiveCompact()
                        if (rc?.isWithheldPromptTooLong(message) == true) {
                            withheld = true
                        }
                        if (isMediaRecoveryEnabled() && rc?.isWithheldMediaSizeError(message) == true) {
                            withheld = true
                        }
                    }
                    if (isWithheldMaxOutputTokens(message)) {
                        withheld = true
                    }
                    
                    if (!withheld) {
                        emit(StreamEvent.AssistantMessage(message))
                    }
                    
                    if (message.type == MessageType.ASSISTANT) {
                        assistantMessages.add(message)
                        
                        // Extract tool use blocks
                        val msgToolUseBlocks = message.contentBlocks.filter { it.type == "tool_use" }
                        if (msgToolUseBlocks.isNotEmpty()) {
                            toolUseBlocks.addAll(msgToolUseBlocks)
                            needsFollowUp = true
                        }
                        
                        // Add to streaming executor
                        if (streamingToolExecutor != null && !toolUseContext.abortController.isAborted) {
                            for (toolBlock in msgToolUseBlocks) {
                                streamingToolExecutor.addTool(toolBlock, message)
                            }
                        }
                    }
                    
                    // Get completed results from streaming executor
                    if (streamingToolExecutor != null && !toolUseContext.abortController.isAborted) {
                        for (result in streamingToolExecutor.getCompletedResults()) {
                            result.message?.let { msg ->
                                emit(StreamEvent.ToolResultMessage(msg))
                                toolResults.add(msg)
                            }
                        }
                    }
                }
            } catch (innerError: Throwable) {
                if (innerError is FallbackTriggeredError && fallbackModel != null) {
                    // Fallback was triggered - switch model and retry
                    currentModel = fallbackModel
                    attemptWithFallback = true
                    
                    // Clear assistant messages
                    yieldMissingToolResultBlocks(assistantMessages, "Model fallback triggered").collect {
                        emit(StreamEvent.ToolResultMessage(it))
                    }
                    assistantMessages.clear()
                    toolResults.clear()
                    toolUseBlocks.clear()
                    needsFollowUp = false
                    
                    // Discard pending results
                    streamingToolExecutor?.discard()
                    
                    // Update tool use context with new model
                    toolUseContext = toolUseContext.copy(
                        options = toolUseContext.options.copy(mainLoopModel = fallbackModel)
                    )
                    
                    // Log fallback event
                    logEvent("tengu_model_fallback_triggered", mapOf(
                        "original_model" to innerError.originalModel,
                        "fallback_model" to fallbackModel
                    ))
                    
                    // Yield system message about fallback
                    emit(StreamEvent.AssistantMessage(createSystemMessage(
                        "Switched to $fallbackModel due to high demand for $currentModel"
                    )))
                    
                    continue
                }
                throw innerError
            }
        }
        
        // Handle errors
        // Execute post-sampling hooks
        if (assistantMessages.isNotEmpty()) {
            executePostSamplingHooks(
                messagesForQuery + assistantMessages,
                systemPrompt,
                userContext,
                systemContext,
                toolUseContext,
                querySource
            )
        }
        
        // Handle streaming abort
        if (toolUseContext.abortController.isAborted) {
            if (streamingToolExecutor != null) {
                streamingToolExecutor.getRemainingResults().collect { update ->
                    update.message?.let { emit(StreamEvent.ToolResultMessage(it)) }
                }
            } else {
                yieldMissingToolResultBlocks(assistantMessages, "Interrupted by user").collect {
                    emit(StreamEvent.ToolResultMessage(it))
                }
            }
            
            // Create interruption message
            if (toolUseContext.abortController.reason != "interrupt") {
                emit(StreamEvent.AssistantMessage(createUserInterruptionMessage(toolUse = false)))
            }
            return@flow
        }
        
        // Yield pending tool use summary
        pendingToolUseSummary?.await()?.let { summary ->
            emit(StreamEvent.ToolUseSummaryMessage(summary.summary, summary.toolUseIds))
        }
        
        if (!needsFollowUp) {
            val lastMessage = assistantMessages.lastOrNull()
            
            // Handle prompt-too-long recovery
            val isWithheld413 = lastMessage?.let { 
                it.type == MessageType.ASSISTANT && it.isApiErrorMessage() && isPromptTooLongMessage(it)
            } ?: false
            
            val mediaRecoveryEnabled = isMediaRecoveryEnabled()
            val isWithheldMedia = mediaRecoveryEnabled && lastMessage?.let {
                isWithheldMediaSizeError(it)
            } ?: false
            
            if (isWithheld413) {
                // First: drain staged context-collapses
                if (FeatureFlags.isEnabled("CONTEXT_COLLAPSE") && 
                    state.transition !is Continue.CollapseDrainRetry) {
                    val drained = recoverFromContextCollapse(messagesForQuery, querySource)
                    if (drained.committed > 0) {
                        state = state.copy(
                            messages = drained.messages,
                            toolUseContext = toolUseContext,
                            autoCompactTracking = autoCompactTracking,
                            transition = Continue.CollapseDrainRetry(drained.committed)
                        )
                        continue
                    }
                }
            }
            
            // Try reactive compact
            if ((isWithheld413 || isWithheldMedia) && isReactiveCompactEnabled()) {
                val compacted = tryReactiveCompact(
                    hasAttempted = hasAttemptedReactiveCompact,
                    querySource = querySource,
                    aborted = toolUseContext.abortController.isAborted,
                    messages = messagesForQuery,
                    cacheSafeParams = CacheSafeParams(
                        systemPrompt = systemPrompt,
                        userContext = userContext,
                        systemContext = systemContext,
                        toolUseContext = toolUseContext,
                        forkContextMessages = messagesForQuery
                    )
                )
                
                if (compacted != null) {
                    // Task budget carryover
                    if (params.taskBudget != null) {
                        val preCompactContext = getFinalContextTokens(messagesForQuery)
                        taskBudgetRemaining = maxOf(0, (taskBudgetRemaining ?: params.taskBudget.total) - preCompactContext)
                    }
                    
                    val postCompactMessages = buildPostCompactMessagesFromReactive(compacted)
                    for (msg in postCompactMessages) {
                        emit(msg)
                    }
                    
                    state = state.copy(
                        messages = postCompactMessages,
                        toolUseContext = toolUseContext,
                        autoCompactTracking = null,
                        hasAttemptedReactiveCompact = true,
                        transition = Continue.ReactiveCompactRetry()
                    )
                    continue
                }
                
                // No recovery - surface withheld error
                lastMessage?.let { emit(StreamEvent.AssistantMessage(it)) }
                executeStopFailureHooks(lastMessage, toolUseContext)
                return@flow
            }
            
            // Handle max_output_tokens and inject recovery
            if (isWithheldMaxOutputTokens(lastMessage)) {
                // Escalating retry: if we hit the limit with capped 8k, retry at 64k
                val capEnabled = isFeatureEnabled("tengu_otk_slot_v1")
                if (capEnabled && maxOutputTokensOverride == null && !hasOutputTokensEnv()) {
                    logEvent("tengu_max_tokens_escalate", mapOf("escalatedTo" to ESCALATED_MAX_TOKENS))
                    state = state.copy(
                        maxOutputTokensOverride = ESCALATED_MAX_TOKENS,
                        transition = Continue.MaxOutputTokensEscalate()
                    )
                    continue
                }
                
                if (maxOutputTokensRecoveryCount < MAX_OUTPUT_TOKENS_RECOVERY_LIMIT) {
                    val recoveryMessage = createUserMessage(
                        "Output token limit hit. Resume directly — no apology, no recap. " +
                        "Pick up mid-thought if that's where the cut happened.",
                        isMeta = true
                    )
                    
                    state = state.copy(
                        messages = messagesForQuery + assistantMessages + recoveryMessage,
                        maxOutputTokensRecoveryCount = maxOutputTokensRecoveryCount + 1,
                        transition = Continue.MaxOutputTokensRecovery(maxOutputTokensRecoveryCount + 1)
                    )
                    continue
                }
                
                // Recovery exhausted - surface withheld error
                lastMessage?.let { emit(StreamEvent.AssistantMessage(it)) }
            }
            
            // Skip stop hooks when last message is API error
            if (lastMessage?.isApiErrorMessage() == true) {
                executeStopFailureHooks(lastMessage, toolUseContext)
                return@flow
            }
            
            // Handle stop hooks
            val stopHookResult = handleStopHooks(
                messagesForQuery,
                assistantMessages,
                systemPrompt,
                userContext,
                systemContext,
                toolUseContext,
                querySource,
                stopHookActive
            )
            
            if (stopHookResult.preventContinuation) {
                return@flow
            }
            
            if (stopHookResult.blockingErrors.isNotEmpty()) {
                state = state.copy(
                    messages = messagesForQuery + assistantMessages + stopHookResult.blockingErrors,
                    maxOutputTokensRecoveryCount = 0,
                    stopHookActive = true,
                    transition = Continue.StopHookBlocking()
                )
                continue
            }
            
            // Token budget check
            if (FeatureFlags.isEnabled("TOKEN_BUDGET") && budgetTracker != null) {
                val decision = checkTokenBudget(
                    budgetTracker,
                    toolUseContext.agentId,
                    getCurrentTurnTokenBudget(),
                    getTurnOutputTokens()
                )
                
                if (decision is ContinueDecision) {
                    incrementBudgetContinuationCount()
                    state = state.copy(
                        messages = messagesForQuery + assistantMessages + createUserMessage(
                            decision.nudgeMessage,
                            isMeta = true
                        ),
                        hasAttemptedReactiveCompact = false,
                        transition = Continue.TokenBudgetContinuation()
                    )
                    continue
                }
            }
            
            // Turn completed successfully
            return@flow
        }
        
        // Tool execution
        val toolUpdates = if (streamingToolExecutor != null) {
            streamingToolExecutor.getRemainingResults()
        } else {
            runTools(toolUseBlocks, assistantMessages, canUseTool, toolUseContext)
        }
        
        var shouldPreventContinuation = false
        var updatedToolUseContext = toolUseContext
        
        toolUpdates.collect { update ->
            update.message?.let { msg ->
                emit(StreamEvent.ToolResultMessage(msg))
                
                if (msg.type == MessageType.TOOL && msg.attachmentType == "hook_stopped_continuation") {
                    shouldPreventContinuation = true
                }
                
                toolResults.add(msg)
            }
            
            update.newContext?.let { newContext ->
                updatedToolUseContext = newContext.copy(queryTracking = queryTracking)
            }
        }
        
        // Generate tool use summary for next turn
        var nextPendingToolUseSummary: Deferred<ToolUseSummaryMessage?>? = null
        if (config.gates.emitToolUseSummaries && toolUseBlocks.isNotEmpty() && 
            !toolUseContext.abortController.isAborted && toolUseContext.agentId == null) {
            nextPendingToolUseSummary = CoroutineScope(Dispatchers.Default).async {
                generateToolUseSummaryAsync(toolUseBlocks, toolResults)
            }
        }
        
        // Handle abort during tool calls
        if (toolUseContext.abortController.isAborted) {
            if (toolUseContext.abortController.reason != "interrupt") {
                emit(StreamEvent.AssistantMessage(createUserInterruptionMessage(toolUse = true)))
            }
            
            // Check maxTurns
            val nextTurnCountOnAbort = turnCount + 1
            if (maxTurns != null && nextTurnCountOnAbort > maxTurns) {
                emit(StreamEvent.CompactBoundaryMessage("max_turns_reached", 0))
            }
            return@flow
        }
        
        // Check if hook indicated to prevent continuation
        if (shouldPreventContinuation) {
            return@flow
        }
        
        // Update turn counter if compacted
        if (autoCompactTracking?.compacted == true) {
            // Log event
        }
        
        // Get queued commands before processing attachments
        val sleepRan = toolUseBlocks.any { it.name == SLEEP_TOOL_NAME }
        val isMainThread = querySource == QuerySource.REPL_MAIN_THREAD || querySource == QuerySource.SDK
        val currentAgentId = toolUseContext.agentId
        val queuedCommandsSnapshot = getQueuedCommands(sleepRan, isMainThread, currentAgentId)
        
        // Get attachment messages
        getAttachmentMessages(updatedToolUseContext, queuedCommandsSnapshot, 
            messagesForQuery + assistantMessages, querySource).collect { attachment ->
            emit(attachment)
            toolResults.add(attachment)
        }
        
        // Memory prefetch consume
        if (pendingMemoryPrefetch != null && pendingMemoryPrefetch.isCompleted) {
            val memoryAttachments = filterDuplicateMemoryAttachments(
                pendingMemoryPrefetch.getCompleted(),
                toolUseContext.readFileState
            )
            for (memAttachment in memoryAttachments) {
                emit(StreamEvent.CompactBoundaryMessage(memAttachment.type, 0))
                toolResults.add(Message(
                    id = generateUuid(),
                    type = MessageType.ATTACHMENT,
                    content = ""
                ))
            }
        }
        
        // Inject prefetched skill discovery
        if (pendingSkillPrefetch != null) {
            val skillAttachments = collectSkillDiscoveryPrefetch(pendingSkillPrefetch)
            for (att in skillAttachments) {
                emit(StreamEvent.CompactBoundaryMessage(att.type, 0))
                toolResults.add(Message(
                    id = generateUuid(),
                    type = MessageType.ATTACHMENT,
                    content = ""
                ))
            }
        }
        
        // Remove consumed commands from queue
        val consumedCommands = queuedCommandsSnapshot.filter { 
            it.mode == "prompt" || it.mode == "task-notification" 
        }
        for (cmd in consumedCommands) {
            cmd.uuid?.let { uuid ->
                consumedCommandUuids.add(uuid)
                notifyCommandLifecycle(uuid, "started")
            }
        }
        removeFromQueue(consumedCommands)
        
        // Refresh tools between turns
        if (updatedToolUseContext.options.refreshTools != null) {
            val refreshedTools = updatedToolUseContext.options.refreshTools()
            if (refreshedTools != updatedToolUseContext.options.tools) {
                updatedToolUseContext = updatedToolUseContext.copy(
                    options = updatedToolUseContext.options.copy(tools = refreshedTools)
                )
            }
        }
        
        val toolUseContextWithQueryTracking = updatedToolUseContext.copy(queryTracking = queryTracking)
        
        // Increment turn count
        val nextTurnCount = turnCount + 1
        
        // Check max turns limit
        if (maxTurns != null && nextTurnCount > maxTurns) {
            emit(StreamEvent.CompactBoundaryMessage("max_turns_reached", 0))
            return@flow
        }
        
        // Continue to next turn
        state = state.copy(
            messages = messagesForQuery + assistantMessages + toolResults,
            toolUseContext = toolUseContextWithQueryTracking,
            autoCompactTracking = autoCompactTracking,
            turnCount = nextTurnCount,
            maxOutputTokensRecoveryCount = 0,
            hasAttemptedReactiveCompact = false,
            pendingToolUseSummary = nextPendingToolUseSummary,
            maxOutputTokensOverride = null,
            stopHookActive = stopHookActive,
            transition = Continue.NextTurn()
        )
    }
}

/**
 * Streaming tool executor for parallel tool execution.
 */
class StreamingToolExecutor(
    private val tools: List<Tool>,
    private val canUseTool: (String) -> Boolean,
    private val toolUseContext: QueryToolUseContext
) {
    private val pendingTools = mutableListOf<ToolUseBlock>()
    private val completedResults = mutableListOf<ToolExecutionUpdate>()
    
    fun addTool(toolBlock: ToolUseBlock, assistantMessage: Message) {
        pendingTools.add(toolBlock)
    }
    
    fun getCompletedResults(): List<ToolExecutionUpdate> {
        val results = completedResults.toList()
        completedResults.clear()
        return results
    }
    
    fun getRemainingResults(): Flow<ToolExecutionUpdate> = flow {
        // Execute remaining tools and emit results
        for (toolBlock in pendingTools) {
            val result = executeToolStreaming(toolBlock)
            emit(result)
            completedResults.add(result)
        }
        pendingTools.clear()
    }
    
    fun discard() {
        pendingTools.clear()
        completedResults.clear()
    }
    
    private suspend fun executeToolStreaming(toolBlock: ToolUseBlock): ToolExecutionUpdate {
        val tool = tools.find { it.name == toolBlock.name }
        return if (tool != null && canUseTool(toolBlock.name)) {
            val result = tool.execute(toolBlock.input)
            ToolExecutionUpdate(
                message = Message(
                    id = generateUuid(),
                    type = MessageType.TOOL_RESULT,
                    content = result.text,
                    isError = result.isError,
                    toolUseId = toolBlock.id
                )
            )
        } else {
            ToolExecutionUpdate(
                message = Message(
                    id = generateUuid(),
                    type = MessageType.TOOL_RESULT,
                    content = "Tool not available: ${toolBlock.name}",
                    isError = true,
                    toolUseId = toolBlock.id
                )
            )
        }
    }
}

/**
 * Tool execution update.
 */
data class ToolExecutionUpdate(
    val message: Message?,
    val newContext: QueryToolUseContext? = null
)

// Helper functions - to be implemented with actual services

private const val ESCALATED_MAX_TOKENS = 64000
private const val SLEEP_TOOL_NAME = "sleep"

private fun getMessagesAfterCompactBoundary(messages: List<Message>): List<Message> = messages

private fun applyToolResultBudget(
    messages: List<Message>,
    contentReplacementState: ContentReplacementState?,
    querySource: QuerySource
): List<Message> = messages

private data class SnipResult(val messages: List<Message>, val tokensFreed: Int, val boundaryMessage: Message?)

private fun applySnipCompact(messages: List<Message>): SnipResult = SnipResult(messages, 0, null)

private data class MicroCompactInput(
    val messages: List<Message>,
    val toolUseContext: QueryToolUseContext,
    val querySource: QuerySource
)

private data class MicroCompactOutput(
    val messages: List<Message>,
    val compactionInfo: CompactionInfo? = null
)

private data class CompactionInfo(
    val pendingCacheEdits: PendingCacheEdits? = null
)

private data class PendingCacheEdits(
    val baselineCacheDeletedTokens: Int,
    val trigger: String,
    val deletedToolIds: List<String>
)

private fun MicroCompact.invoke(input: MicroCompactInput): MicroCompactOutput = 
    MicroCompactOutput(input.messages)

private fun applyContextCollapse(
    messages: List<Message>,
    toolUseContext: QueryToolUseContext,
    querySource: QuerySource
): List<Message> = messages

private data class CallModelInput(
    val messages: List<Map<String, Any>>,
    val systemPrompt: String,
    val thinkingConfig: ThinkingConfig,
    val tools: List<Tool>,
    val signal: AbortController,
    val options: CallModelOptions
)

private data class CallModelOptions(
    val model: String,
    val fallbackModel: String? = null,
    val onStreamingFallback: (() -> Unit)? = null,
    val querySource: String = "repl_main_thread",
    val agents: List<AgentDefinition> = emptyList(),
    val allowedAgentTypes: List<String> = emptyList(),
    val hasAppendSystemPrompt: Boolean = false,
    val maxOutputTokensOverride: Int? = null,
    val agentId: String? = null,
    val addNotification: ((Notification) -> Unit)? = null,
    val taskBudget: TaskBudgetInput? = null
)

private data class TaskBudgetInput(val total: Int, val remaining: Int?)

private fun CallModel.invoke(input: CallModelInput): Flow<Message> = flow {
    // Placeholder - actual implementation would call the API
}

private data class AutoCompactInput(
    val messages: List<Message>,
    val toolUseContext: QueryToolUseContext,
    val systemPrompt: String,
    val userContext: Map<String, String>,
    val systemContext: Map<String, String>,
    val forkContextMessages: List<Message>
)

private data class AutoCompactOutput(
    val summaryMessages: List<Message> = emptyList(),
    val attachments: List<Message> = emptyList(),
    val hookResults: List<Message> = emptyList(),
    val preCompactTokenCount: Int = 0,
    val postCompactTokenCount: Int = 0,
    val truePostCompactTokenCount: Int = 0,
    val compactionUsage: TokenUsage? = null
)

private data class TokenUsage(
    val input_tokens: Int,
    val output_tokens: Int,
    val cache_read_input_tokens: Int = 0,
    val cache_creation_input_tokens: Int = 0
)

private fun AutoCompact.invoke(
    input: AutoCompactInput,
    querySource: QuerySource,
    tracking: AutoCompactTrackingState?,
    snipTokensFreed: Int
): Pair<AutoCompactOutput?, Int?> = Pair(null, null)

private fun buildPostCompactMessages(result: AutoCompactOutput): List<Message> = 
    result.summaryMessages + result.attachments + result.hookResults

private fun getFinalContextTokens(messages: List<Message>): Int = 0

private fun estimateTokenCount(messages: List<Message>): Int = messages.sumBy { it.content.length / 4 }

private fun isAtBlockingLimit(tokenCount: Int, model: String): Boolean = tokenCount > 150000

private fun isAutoCompactEnabled(): Boolean = FeatureFlags.isEnabled("AUTO_COMPACT")

private fun isReactiveCompactEnabled(): Boolean = FeatureFlags.isEnabled("REACTIVE_COMPACT")

private fun isMediaRecoveryEnabled(): Boolean = FeatureFlags.isEnabled("REACTIVE_COMPACT")

private fun getReactiveCompact(): Any? = null

private fun isWithheldMediaSizeError(msg: Any): Boolean = false

private fun recoverFromContextCollapse(messages: List<Message>, querySource: QuerySource): ContextCollapseResult =
    ContextCollapseResult(messages, 0)

private data class ContextCollapseResult(val messages: List<Message>, val committed: Int)

private fun tryReactiveCompact(
    hasAttempted: Boolean,
    querySource: QuerySource,
    aborted: Boolean,
    messages: List<Message>,
    cacheSafeParams: CacheSafeParams
): ReactiveCompactResult? = null

private data class ReactiveCompactResult(
    val summaryMessages: List<Message>,
    val attachments: List<Message>,
    val hookResults: List<Message>
)

private fun buildPostCompactMessagesFromReactive(result: ReactiveCompactResult): List<Message> =
    result.summaryMessages + result.attachments + result.hookResults

private fun prependUserContext(messages: List<Map<String, Any>>, userContext: Map<String, String>): List<Map<String, Any>> =
    messages

private fun getRuntimeMainLoopModel(permissionMode: String, mainLoopModel: String, exceeds200kTokens: Boolean): String = mainLoopModel

private fun doesMostRecentAssistantMessageExceed200k(messages: List<Message>): Boolean = false

private fun isFeatureEnabled(feature: String): Boolean = FeatureFlags.isEnabled(feature)

private fun hasOutputTokensEnv(): Boolean = System.getenv("COCACODE_MAX_OUTPUT_TOKENS") != null

private fun executeStopFailureHooks(message: Message?, toolUseContext: QueryToolUseContext) {}

private fun createApiErrorMessage(content: String, error: String): Message = Message(
    id = generateUuid(),
    type = MessageType.ASSISTANT,
    content = content,
    apiError = error
)

private fun createUserMessage(content: String, isMeta: Boolean = false): Message = Message(
    id = generateUuid(),
    type = MessageType.USER,
    content = content
)

private fun createSystemMessage(content: String, level: String = "info"): Message = Message(
    id = generateUuid(),
    type = MessageType.SYSTEM,
    content = content
)

private fun createUserInterruptionMessage(toolUse: Boolean): Message = Message(
    id = generateUuid(),
    type = MessageType.USER,
    content = if (toolUse) "Tools interrupted" else "User interrupted"
)

private fun runTools(
    toolUseBlocks: List<ToolUseBlock>,
    assistantMessages: List<Message>,
    canUseTool: (String) -> Boolean,
    toolUseContext: QueryToolUseContext
): Flow<ToolExecutionUpdate> = flow {
    for (toolBlock in toolUseBlocks) {
        val tool = toolUseContext.options.tools.find { it.name == toolBlock.name }
        if (tool != null && canUseTool(toolBlock.name)) {
            val result = tool.execute(toolBlock.input)
            emit(ToolExecutionUpdate(
                message = Message(
                    id = generateUuid(),
                    type = MessageType.TOOL_RESULT,
                    content = result.text,
                    isError = result.isError,
                    toolUseId = toolBlock.id
                )
            ))
        }
    }
}

private data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: Map<String, Any>
)

private val Message.contentBlocks: List<ContentBlock>
    get() = emptyList()

private data class ContentBlock(
    val type: String,
    val id: String = "",
    val name: String = "",
    val input: Map<String, Any> = emptyMap(),
    val text: String = "",
    val content: String = ""
)

private var Message.attachmentType: String?
    get() = null
    set(_) {}

private var Message.toolUseId: String?
    get() = null
    set(_) {}

private var Message.isError: Boolean
    get() = false
    set(_) {}

private fun getQueuedCommands(sleepRan: Boolean, isMainThread: Boolean, currentAgentId: String?): List<QueuedCommand> = emptyList()

private data class QueuedCommand(
    val uuid: String?,
    val mode: String,
    val agentId: String?
)

private fun getAttachmentMessages(
    toolUseContext: QueryToolUseContext,
    queuedCommands: List<QueuedCommand>,
    messages: List<Message>,
    querySource: QuerySource
): Flow<Message> = flow {}

private fun startMemoryPrefetch(messages: List<Message>, toolUseContext: QueryToolUseContext): CompletableDeferred<List<Attachment>>? = null

private fun filterDuplicateMemoryAttachments(attachments: List<Attachment>, fileState: FileStateCache): List<Attachment> = attachments

private fun startSkillDiscoveryPrefetch(messages: List<Message>, toolUseContext: QueryToolUseContext): Any? = null

private fun collectSkillDiscoveryPrefetch(prefetch: Any): List<Attachment> = emptyList()

private fun removeFromQueue(commands: List<QueuedCommand>) {}

private fun notifyCommandLifecycle(uuid: String, status: String) {}

private fun logEvent(eventName: String, params: Map<String, Any>) {}

private fun executePostSamplingHooks(
    messages: List<Message>,
    systemPrompt: String,
    userContext: Map<String, String>,
    systemContext: Map<String, String>,
    toolUseContext: QueryToolUseContext,
    querySource: QuerySource
) {}

private suspend fun generateToolUseSummaryAsync(
    toolUseBlocks: List<ToolUseBlock>,
    toolResults: List<Message>
): ToolUseSummaryMessage? = null

private fun getCurrentTurnTokenBudget(): Int? = null

private fun getTurnOutputTokens(): Int = 0

private fun incrementBudgetContinuationCount() {}

private val Message.type: MessageType
    get() = MessageType.ASSISTANT
