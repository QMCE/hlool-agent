package rj.cocacode.services.oauth

import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import kotlin.text.Charsets.UTF_8
import io.ktor.http.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import rj.cocacode.services.api.HttpClient

object OAuthCrypto {
    private val secureRandom = SecureRandom()
    
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    fun generateCodeChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
    
    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object OAuthService {
    private const val AUTH_URL = "https://auth.anthropic.com/oauth/authorize"
    private const val TOKEN_URL = "https://auth.anthropic.com/oauth/token"
    private const val CLIENT_ID = "anthropic-client"
    
    fun buildAuthorizationUrl(
        redirectUri: String,
        state: String,
        codeChallenge: String,
        scope: String = "profile:read organization:read",
        loginHint: String? = null
    ): String {
        val params = listOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to redirectUri,
            "scope" to scope,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        ) + if (loginHint != null) listOf("login_hint" to loginHint) else emptyList()
        
        return "$AUTH_URL?${params.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, UTF_8.name())}" }}"
    }
    
    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OAuthTokens? {
        return try {
            val params = mapOf(
                "grant_type" to "authorization_code",
                "client_id" to CLIENT_ID,
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to codeVerifier
            )
            
            val response = HttpClient.client.post(TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value.toString(), UTF_8.name())}" })
            }
            
            val body: String = response.bodyAsText()
            parseTokenResponse(body)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTokenResponse(json: String): OAuthTokens? {
        return try {
            val data = com.google.gson.Gson().fromJson(json, Map::class.java)
            OAuthTokens(
                accessToken = data["access_token"] as String,
                refreshToken = data["refresh_token"] as? String,
                expiresIn = (data["expires_in"] as? Number)?.toLong()
            )
        } catch (e: Exception) { null }
    }
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null
)

data class OAuthProfile(
    val id: String,
    val email: String,
    val name: String?
)