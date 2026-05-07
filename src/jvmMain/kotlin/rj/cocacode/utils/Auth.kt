package rj.cocacode.utils

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
)

sealed class AuthResult {
    data class Success(val tokens: AuthTokens) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object NotAuthenticated : AuthResult()
}

object AuthManager {
    private var currentTokens: AuthTokens? = null
    
    fun isAuthenticated(): Boolean = currentTokens?.accessToken?.isNotEmpty() == true
    
    fun getAccessToken(): String? = currentTokens?.accessToken
    
    fun setTokens(tokens: AuthTokens) {
        currentTokens = tokens
    }
    
    fun clearTokens() {
        currentTokens = null
    }
    
    fun isTokenExpired(): Boolean {
        val expiresAt = currentTokens?.expiresAt ?: return true
        return System.currentTimeMillis() > expiresAt
    }
    
    fun getAuthStatus(): AuthResult {
        val tokens = currentTokens ?: return AuthResult.NotAuthenticated
        return if (isTokenExpired()) {
            if (tokens.refreshToken != null) {
                AuthResult.Success(tokens)
            } else {
                AuthResult.NotAuthenticated
            }
        } else {
            AuthResult.Success(tokens)
        }
    }
}