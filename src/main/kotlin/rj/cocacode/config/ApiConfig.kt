package rj.cocacode.config

object ApiConfig {
    private var cachedConfig: rj.cocacode.utils.Config? = null

    private fun getConfig(): rj.cocacode.utils.Config {
        cachedConfig?.let { return it }
        return rj.cocacode.utils.ConfigManager.getGlobalConfig().also { cachedConfig = it }
    }

    fun reload() {
        cachedConfig = null
    }

    val baseUrl: String
        get() = getConfig().apiUrl
            ?: System.getenv("API_BASE_URL")
            ?: "https://api.anthropic.com"

    val apiKey: String
        get() = getConfig().apiKey
            ?: System.getenv("ANTHROPIC_API_KEY")
            ?: System.getenv("API_KEY")
            ?: ""

    val model: String
        get() = getConfig().model
            ?: System.getenv("DEFAULT_MODEL")
            ?: "claude-sonnet-4-20250514"
}
