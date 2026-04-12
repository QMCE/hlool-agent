package rj.cocacode.constants

object Product {
    const val URL = "https://cocacode.ai"
    const val BASE_URL = "https://cocacode.ai"
    const val STAGING_BASE_URL = "https://cocacode.staging.dev"
    const val LOCAL_BASE_URL = "http://localhost:4000"
    
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