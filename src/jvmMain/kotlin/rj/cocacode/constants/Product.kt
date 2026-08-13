package rj.cocacode.constants

/**
 * Product branding and default service endpoints.
 *
 * Hlool Agent is built for SHUAI API (api.shuaiapi.com): one console API key
 * unlocks the same OpenAI-compatible surface the web console uses.
 */
object Product {
    const val NAME = "Hlool Agent"
    const val CLI_NAME = "hlool-agent"
    const val SHORT_NAME = "hlool"

    /** Primary config directory under the user home (~/.hlool-agent). */
    const val CONFIG_DIR_NAME = ".hlool-agent"

    /** Legacy CocaCode config dir — still read as a fallback when migrating. */
    const val LEGACY_CONFIG_DIR_NAME = ".cocacode"

    /** Default SHUAI / Hlool API gateway (NewAPI). */
    const val API_BASE_URL = "https://api.shuaiapi.com"

    /** Web console (login, OAuth, token management). */
    const val CONSOLE_URL = "https://api.shuaiapi.com"
    const val LOGIN_URL = "$CONSOLE_URL/login"
    const val TOKEN_CONSOLE_URL = "$CONSOLE_URL/console/token"

    const val URL = CONSOLE_URL
    const val BASE_URL = CONSOLE_URL
    const val STAGING_BASE_URL = "https://api.shuaiapi.com"
    const val LOCAL_BASE_URL = "http://localhost:4000"

    /** Default model when none is configured (SHUAI recommended set). */
    const val DEFAULT_MODEL = "claude-sonnet-4-5-20250929"

    fun isRemoteSessionStaging(sessionId: String? = null, ingressUrl: String? = null): Boolean =
        sessionId?.contains("_staging_") == true || ingressUrl?.contains("staging") == true

    fun isRemoteSessionLocal(sessionId: String? = null, ingressUrl: String? = null): Boolean =
        sessionId?.contains("_local_") == true || ingressUrl?.contains("localhost") == true

    fun getBaseUrl(sessionId: String? = null, ingressUrl: String? = null): String =
        when {
            isRemoteSessionLocal(sessionId, ingressUrl) -> LOCAL_BASE_URL
            isRemoteSessionStaging(sessionId, ingressUrl) -> STAGING_BASE_URL
            else -> BASE_URL
        }

    fun getRemoteSessionUrl(sessionId: String, ingressUrl: String? = null): String {
        val baseUrl = getBaseUrl(sessionId, ingressUrl)
        return "$baseUrl/code/$sessionId"
    }
}
