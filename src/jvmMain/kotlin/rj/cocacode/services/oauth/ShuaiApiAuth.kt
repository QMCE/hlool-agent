package rj.cocacode.services.oauth

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import rj.cocacode.constants.Product
import rj.cocacode.services.api.HttpClient
import java.awt.Desktop
import java.net.URI

/**
 * Auth against SHUAI API (NewAPI / Hlool gateway at api.shuaiapi.com).
 *
 * Model:
 *  - Web OAuth (GitHub / LinuxDo / Passkey) lives in the browser console.
 *  - Relay (chat / models / drawing / tasks) uses a single `sk-…` API token
 *    with `Authorization: Bearer` — the same key the web “一键填入” clients use.
 *  - Password login can mint that sk token via the management API so the CLI
 *    never needs a custom OAuth redirect URI.
 *
 * “1 key = full web surface” ⇒ save that sk token as [apiKey] with
 * baseUrl = [Product.API_BASE_URL] and apiType = chat.
 */
object ShuaiApiAuth {
    private val gson = Gson()

    data class SiteStatus(
        val systemName: String,
        val serverAddress: String,
        val githubOAuth: Boolean,
        val linuxdoOAuth: Boolean,
        val passwordLogin: Boolean,
        val passkeyLogin: Boolean
    )

    data class LoginResult(
        val accessToken: String,
        val userId: Int?,
        val username: String?
    )

    data class CreatedToken(
        val id: Int,
        val key: String,
        val name: String
    )

    suspend fun fetchStatus(baseUrl: String = Product.API_BASE_URL): SiteStatus? {
        return try {
            val text = HttpClient.client.get("${baseUrl.trimEnd('/')}/api/status").bodyAsText()
            val root = gson.fromJson(text, JsonObject::class.java)
            val data = root.getAsJsonObject("data") ?: return null
            SiteStatus(
                systemName = data.get("system_name")?.asString ?: "SHUAI API",
                serverAddress = data.get("server_address")?.asString ?: baseUrl,
                githubOAuth = data.get("github_oauth")?.asBoolean == true,
                linuxdoOAuth = data.get("linuxdo_oauth")?.asBoolean == true,
                passwordLogin = data.get("password_login_enabled")?.asBoolean != false,
                passkeyLogin = data.get("passkey_login")?.asBoolean == true
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validate a relay API key the same way web clients do: GET /v1/models.
     * Returns null on success, or an error message.
     */
    suspend fun validateApiKey(apiKey: String, baseUrl: String = Product.API_BASE_URL): String? {
        val key = normalizeKey(apiKey) ?: return "API key looks empty"
        return try {
            val response = HttpClient.client.get("${baseUrl.trimEnd('/')}/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $key")
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) null
            else "Key rejected (${response.status.value}): ${body.take(200)}"
        } catch (e: Exception) {
            "Could not reach ${baseUrl}: ${e.message}"
        }
    }

    /** Password login → management access token (not the sk relay key). */
    suspend fun passwordLogin(
        username: String,
        password: String,
        baseUrl: String = Product.API_BASE_URL
    ): Result<LoginResult> {
        return try {
            val response = HttpClient.client.post("${baseUrl.trimEnd('/')}/api/user/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":${jsonString(username)},"password":${jsonString(password)}}""")
            }
            val body = response.bodyAsText()
            val root = gson.fromJson(body, JsonObject::class.java)
            if (root.get("success")?.asBoolean != true) {
                return Result.failure(IllegalStateException(root.get("message")?.asString ?: "Login failed"))
            }
            val data = root.getAsJsonObject("data")
                ?: return Result.failure(IllegalStateException("Login response missing data"))
            if (data.get("require_2fa")?.asBoolean == true) {
                return Result.failure(IllegalStateException("Account requires 2FA — use /login with a console sk- key instead"))
            }
            val token = data.get("access_token")?.asString
                ?: data.get("token")?.asString
                ?: return Result.failure(IllegalStateException("Login response missing access token"))
            val user = data.getAsJsonObject("user")
            Result.success(
                LoginResult(
                    accessToken = token,
                    userId = user?.get("id")?.asInt,
                    username = user?.get("username")?.asString
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create an unlimited sk token named for Hlool Agent, then fetch the raw key.
     * NewAPI's AddToken does not return the key; we list + /api/token/:id/key.
     */
    suspend fun createRelayToken(
        accessToken: String,
        baseUrl: String = Product.API_BASE_URL,
        name: String = "Hlool Agent"
    ): Result<CreatedToken> {
        return try {
            val create = HttpClient.client.post("${baseUrl.trimEnd('/')}/api/token/") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    """{"name":${jsonString(name)},"remain_quota":0,"unlimited_quota":true,"expired_time":-1}"""
                )
            }
            val createBody = create.bodyAsText()
            val createJson = gson.fromJson(createBody, JsonObject::class.java)
            if (createJson.get("success")?.asBoolean != true) {
                return Result.failure(
                    IllegalStateException(createJson.get("message")?.asString ?: "Failed to create token")
                )
            }

            val list = HttpClient.client.get("${baseUrl.trimEnd('/')}/api/token/?p=0&page_size=20") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            val listBody = list.bodyAsText()
            val listJson = gson.fromJson(listBody, JsonObject::class.java)
            if (listJson.get("success")?.asBoolean != true) {
                return Result.failure(IllegalStateException("Could not list tokens after create"))
            }
            val items = listJson.getAsJsonObject("data")?.getAsJsonArray("items")
                ?: listJson.getAsJsonArray("data")
                ?: return Result.failure(IllegalStateException("Unexpected token list shape"))

            var tokenId: Int? = null
            var tokenName = name
            for (el in items) {
                val obj = el.asJsonObject
                if (obj.get("name")?.asString == name) {
                    tokenId = obj.get("id")?.asInt
                    tokenName = obj.get("name").asString
                    break
                }
            }
            if (tokenId == null && items.size() > 0) {
                val first = items[0].asJsonObject
                tokenId = first.get("id")?.asInt
                tokenName = first.get("name")?.asString ?: name
            }
            val id = tokenId ?: return Result.failure(IllegalStateException("Created token id not found"))

            val keyResp = HttpClient.client.get("${baseUrl.trimEnd('/')}/api/token/$id/key") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            val keyBody = keyResp.bodyAsText()
            val keyJson = gson.fromJson(keyBody, JsonObject::class.java)
            if (keyJson.get("success")?.asBoolean != true) {
                return Result.failure(
                    IllegalStateException(keyJson.get("message")?.asString ?: "Failed to read token key")
                )
            }
            val raw = keyJson.getAsJsonObject("data")?.get("key")?.asString
                ?: return Result.failure(IllegalStateException("Token key missing in response"))
            val key = if (raw.startsWith("sk-")) raw else "sk-$raw"
            Result.success(CreatedToken(id = id, key = key, name = tokenName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun normalizeKey(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed
    }

    /** Open the SHUAI console login / token pages in the system browser. */
    fun openBrowserAuth(baseUrl: String = Product.API_BASE_URL): Boolean {
        val login = "${baseUrl.trimEnd('/')}/login"
        val tokens = "${baseUrl.trimEnd('/')}/console/token"
        return openUrl(login) && openUrl(tokens)
    }

    fun openUrl(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                // Headless / Linux without Desktop: best-effort xdg-open / open
                val os = System.getProperty("os.name").lowercase()
                val cmd = when {
                    os.contains("mac") -> arrayOf("open", url)
                    os.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
                    else -> arrayOf("xdg-open", url)
                }
                ProcessBuilder(*cmd).start()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun jsonString(value: String): String =
        gson.toJson(value)
}
