package rj.cocacode.constants

object System {
    const val CLI_SYSPROMPT_PREFIX = "You are CocaCode, an AI coding assistant."
    const val CLI_SYSPROMPT_SDK_PREFIX = "You are CocaCode, running within the Agent SDK."
    
    const val DEFAULT_MAX_TOKENS = 4096
    const val DEFAULT_TEMPERATURE = 1.0
    const val MAX_CONTEXT_TOKENS = 200000
    
    const val TOOL_TIMEOUT_MS = 60000L
    const val MAX_TOOL_RETRIES = 3
    
    const val SESSION_TIMEOUT_MS = 3600000L
    const val IDLE_TIMEOUT_MS = 300000L
    
    const val MAX_MESSAGES_IN_MEMORY = 1000
    const val MAX_CONVERSATION_LENGTH = 100
    
    const val ENABLE_THINKING = true
    const val THINKING_TOKEN_BUDGET = 1024
}

object ApiDefaults {
    const val BASE_URL = "https://api.anthropic.com"
    const val VERSION = "2023-06-01"
    const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
    
    val SUPPORTED_MODELS = listOf(
        "claude-opus-4-20250514",
        "claude-sonnet-4-20250514",
        "claude-haiku-20240307"
    )
    
    val FAST_MODELS = listOf(
        "claude-haiku-20240307"
    )
}

object ToolLimits {
    const val MAX_FILE_SIZE = 10 * 1024 * 1024
    const val MAX_SEARCH_RESULTS = 50
    const val MAX_GLOB_RESULTS = 100
    const val MAX_BASH_OUTPUT = 100 * 1024
}