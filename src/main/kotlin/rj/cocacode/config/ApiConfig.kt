package rj.cocacode.config

/**
 * API configuration for external services.
 */
data class ApiConfig(
    val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    val baseUrl: String = "https://api.anthropic.com",
    val model: String = "claude-sonnet-4-20250514",
    val maxTokens: Int = 4096,
    val timeout: Long = 60000
) {
    companion object {
        fun reload() {
            // Reload from config file
        }
    }
}
