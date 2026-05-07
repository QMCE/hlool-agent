package rj.cocacode.query

import rj.cocacode.state.AppStateManager
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.utils.ThinkingConfig
import rj.cocacode.utils.shouldEnableThinkingByDefault
import rj.cocacode.config.ApiConfig
import rj.cocacode.constants.System

suspend fun fetchSystemPromptParts(
    mainLoopModel: String,
    customSystemPrompt: String?
): SystemPromptParts {
    val defaultSystemPrompt: List<String>
    val userContext: Map<String, String>
    val systemContext: Map<String, String>

    if (customSystemPrompt != null) {
        defaultSystemPrompt = emptyList()
        userContext = getUserContext()
        systemContext = emptyMap()
    } else {
        defaultSystemPrompt = getSystemPrompt()
        userContext = getUserContext()
        systemContext = getSystemContext()
    }

    return SystemPromptParts(defaultSystemPrompt, userContext, systemContext)
}

data class SystemPromptParts(
    val defaultSystemPrompt: List<String>,
    val userContext: Map<String, String>,
    val systemContext: Map<String, String>
)

suspend fun buildSideQuestionFallbackParams(
    messages: List<Message>,
    customSystemPrompt: String?,
    appendSystemPrompt: String?,
    thinkingConfig: ThinkingConfig?
): CacheSafeParams {
    val mainLoopModel = ApiConfig.model

    val systemPromptParts = fetchSystemPromptParts(
        mainLoopModel = mainLoopModel,
        customSystemPrompt = customSystemPrompt
    )

    val promptParts = mutableListOf<String>()
    if (customSystemPrompt != null) {
        promptParts.add(customSystemPrompt)
    } else {
        promptParts.addAll(systemPromptParts.defaultSystemPrompt)
    }
    if (!appendSystemPrompt.isNullOrEmpty()) {
        promptParts.add(appendSystemPrompt)
    }
    val systemPrompt = promptParts.joinToString("\n\n")

    val forkContextMessages = messages

    val effectiveThinkingConfig = thinkingConfig
        ?: if (shouldEnableThinkingByDefault() != false) {
            ThinkingConfig.Adaptive
        } else {
            ThinkingConfig.Disabled
        }

    return CacheSafeParams(
        systemPrompt = systemPrompt,
        userContext = systemPromptParts.userContext,
        systemContext = systemPromptParts.systemContext,
        toolUseContext = effectiveThinkingConfig,
        forkContextMessages = forkContextMessages
    )
}

data class CacheSafeParams(
    val systemPrompt: String,
    val userContext: Map<String, String>,
    val systemContext: Map<String, String>,
    val toolUseContext: ThinkingConfig,
    val forkContextMessages: List<Message>
)

private fun getUserContext(): Map<String, String> {
    return mapOf(
        "sessionId" to (AppStateManager.getState().sessionId ?: "")
    )
}

private fun getSystemContext(): Map<String, String> {
    val state = AppStateManager.getState()
    return mapOf(
        "theme" to state.theme,
        "debugMode" to state.debugMode.toString()
    )
}

private fun getSystemPrompt(): List<String> {
    return listOf(System.CLI_SYSPROMPT_PREFIX)
}