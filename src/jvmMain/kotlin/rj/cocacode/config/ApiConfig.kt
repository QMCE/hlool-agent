package rj.cocacode.config

import rj.cocacode.constants.Product
import rj.cocacode.utils.ConfigManager

/**
 * Which API interface the configured endpoint speaks.
 */
enum class ApiType {
    /** OpenAI-compatible: POST {baseUrl}/v1/chat/completions, Bearer auth. */
    CHAT,

    /** Anthropic Messages API: POST {baseUrl}/v1/messages, x-api-key auth. */
    MESSAGES
}

/**
 * API configuration for external services.
 *
 * Loaded from, in priority order:
 *  1. Environment variables (HLOOL_* preferred)
 *  2. User config `~/.hlool-agent/config.json`
 *  3. Built-in SHUAI defaults
 *
 * Auth: prefer **access token** (management). Relay `apiKey` (sk-) is
 * auto-claimed and persisted — users never paste sk- keys.
 */
data class ApiConfig(
    val apiKey: String = "",
    val accessToken: String = "",
    val userId: Int? = null,
    val baseUrl: String = Product.API_BASE_URL,
    val model: String = Product.DEFAULT_MODEL,
    val maxTokens: Int = 4096,
    val timeout: Long = 120000,
    val apiType: ApiType = ApiType.CHAT,
    /** Token budget for thinking; 0 disables. Defaults to enabled (16000). */
    val thinkingBudget: Int = 16000
) {
    companion object {
        /** Current effective configuration. Call [reload] after config changes. */
        var default: ApiConfig = ApiConfig()
            private set

        val model: String get() = default.model
        val apiKey: String get() = default.apiKey
        val accessToken: String get() = default.accessToken
        val userId: Int? get() = default.userId
        val baseUrl: String get() = default.baseUrl
        val maxTokens: Int get() = default.maxTokens
        val timeout: Long get() = default.timeout
        val apiType: ApiType get() = default.apiType
        val thinkingBudget: Int get() = default.thinkingBudget

        /** Logged in when access token or (legacy) relay key is present. */
        val isConfigured: Boolean
            get() = default.accessToken.isNotBlank() || default.apiKey.isNotBlank()

        val hasAccessToken: Boolean get() = default.accessToken.isNotBlank()

        /** Reload configuration. Env vars always win over the config file. */
        fun reload() {
            val file = try {
                ConfigManager.getGlobalConfig()
            } catch (e: Exception) {
                null
            }

            val baseUrl = env("HLOOL_BASE_URL")
                ?: env("COCA_BASE_URL")
                ?: file?.baseUrl
                ?: file?.apiUrl
                ?: Product.API_BASE_URL

            default = ApiConfig(
                apiKey = env("HLOOL_API_KEY")
                    ?: env("COCA_API_KEY")
                    ?: file?.apiKey.orEmpty(),
                accessToken = env("HLOOL_ACCESS_TOKEN")
                    ?: env("NEWAPI_ACCESS_TOKEN")
                    ?: file?.accessToken.orEmpty(),
                userId = env("HLOOL_USER_ID")?.toIntOrNull()
                    ?: env("NEWAPI_USER_ID")?.toIntOrNull()
                    ?: file?.userId,
                baseUrl = normalizeBaseUrl(baseUrl),
                model = env("HLOOL_MODEL")
                    ?: env("COCA_MODEL")
                    ?: file?.model
                    ?: Product.DEFAULT_MODEL,
                maxTokens = env("HLOOL_MAX_TOKENS")?.toIntOrNull()
                    ?: env("COCA_MAX_TOKENS")?.toIntOrNull()
                    ?: file?.maxTokens
                    ?: 4096,
                timeout = env("HLOOL_API_TIMEOUT")?.toLongOrNull()
                    ?: env("COCA_API_TIMEOUT")?.toLongOrNull()
                    ?: 120000L,
                apiType = parseApiType(
                    env("HLOOL_API_TYPE") ?: env("COCA_API_TYPE") ?: file?.apiType,
                    baseUrl
                ),
                thinkingBudget = env("HLOOL_MAX_THINKING_TOKENS")?.toIntOrNull()
                    ?: env("COCA_MAX_THINKING_TOKENS")?.toIntOrNull()
                    ?: env("MAX_THINKING_TOKENS")?.toIntOrNull()
                    ?: file?.maxThinkingTokens
                    ?: 16000
            )
        }

        fun parseApiType(type: String?, baseUrl: String): ApiType = when (type?.trim()?.lowercase()) {
            "chat", "openai", "openai-compatible" -> ApiType.CHAT
            "messages", "anthropic" -> ApiType.MESSAGES
            else -> if (baseUrl.contains("api.anthropic.com")) ApiType.MESSAGES else ApiType.CHAT
        }

        private fun normalizeBaseUrl(url: String): String = url
            .trimEnd('/')
            .removeSuffix("/v1/chat/completions")
            .removeSuffix("/chat/completions")
            .removeSuffix("/v1/messages")
            .removeSuffix("/messages")
            .trimEnd('/')

        private fun env(name: String): String? =
            System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}
