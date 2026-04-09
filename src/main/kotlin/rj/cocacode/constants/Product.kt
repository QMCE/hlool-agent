package rj.cocacode.constants

object Product {
    const val URL = "https://claude.com/claude-code"
    const val CLAUDE_AI_BASE_URL = "https://claude.ai"
    const val CLAUDE_AI_STAGING_BASE_URL = "https://claude-ai.staging.ant.dev"
    const val CLAUDE_AI_LOCAL_BASE_URL = "http://localhost:4000"
    
    fun isRemoteSessionStaging(sessionId: String? = null, ingressUrl: String? = null): Boolean =
        sessionId?.contains("_staging_") == true || ingressUrl?.contains("staging") == true
    
    fun isRemoteSessionLocal(sessionId: String? = null, ingressUrl: String? = null): Boolean =
        sessionId?.contains("_local_") == true || ingressUrl?.contains("localhost") == true
    
    fun getClaudeAiBaseUrl(sessionId: String? = null, ingressUrl: String? = null): String =
        when {
            isRemoteSessionLocal(sessionId, ingressUrl) -> CLAUDE_AI_LOCAL_BASE_URL
            isRemoteSessionStaging(sessionId, ingressUrl) -> CLAUDE_AI_STAGING_BASE_URL
            else -> CLAUDE_AI_BASE_URL
        }
    
    fun getRemoteSessionUrl(sessionId: String, ingressUrl: String? = null): String {
        val baseUrl = getClaudeAiBaseUrl(sessionId, ingressUrl)
        return "$baseUrl/code/$sessionId"
    }
}