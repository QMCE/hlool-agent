package rj.cocacode.services.oauth

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import rj.cocacode.config.ApiConfig
import rj.cocacode.constants.Product
import rj.cocacode.services.api.HttpClient
import rj.cocacode.utils.Config
import rj.cocacode.utils.ConfigManager
import java.awt.Desktop
import java.net.URI

/**
 * SHUAI / NewAPI console client.
 *
 * Credential model (no manual sk- paste):
 *  - Persist **access token** (management PAT) + userId
 *  - Auto-claim a named relay sk- via `/api/token/` and persist it as [Config.apiKey]
 *  - Access token unlocks the same management surface as the web console
 */
object ShuaiApiAuth {
    private val gson = Gson()
    const val RELAY_TOKEN_NAME = "Hlool Agent"

    data class SiteStatus(
        val systemName: String,
        val serverAddress: String,
        val githubOAuth: Boolean,
        val linuxdoOAuth: Boolean,
        val passwordLogin: Boolean,
        val passkeyLogin: Boolean,
        val quotaPerUnit: Double
    )

    data class Session(
        val accessToken: String,
        val userId: Int,
        val username: String?,
        val displayName: String? = null,
        val quota: Long = 0,
        val usedQuota: Long = 0,
        val group: String? = null,
        val requestCount: Long = 0
    )

    data class RelayToken(
        val id: Int,
        val key: String,
        val name: String,
        val group: String? = null,
        val status: Int = 1
    )

    data class TokenRow(
        val id: Int,
        val name: String,
        val keyMasked: String,
        val status: Int,
        val group: String,
        val remainQuota: Long,
        val unlimitedQuota: Boolean,
        val expiredTime: Long,
        val usedQuota: Long
    )

    data class LogRow(
        val id: Long,
        val modelName: String,
        val tokenName: String,
        val quota: Long,
        val promptTokens: Int,
        val completionTokens: Int,
        val content: String,
        val createdAt: Long,
        val type: Int
    )

    // --- Status / browser -------------------------------------------------

    suspend fun fetchStatus(baseUrl: String = Product.API_BASE_URL): SiteStatus? = try {
        val root = getJson("${baseUrl.trimEnd('/')}/api/status")
        val data = root.getAsJsonObject("data") ?: return null
        SiteStatus(
            systemName = data.str("system_name") ?: "SHUAI API",
            serverAddress = data.str("server_address") ?: baseUrl,
            githubOAuth = data.bool("github_oauth"),
            linuxdoOAuth = data.bool("linuxdo_oauth"),
            passwordLogin = data.get("password_login_enabled")?.asBoolean != false,
            passkeyLogin = data.bool("passkey_login"),
            quotaPerUnit = data.get("quota_per_unit")?.asDouble ?: 500_000.0
        )
    } catch (_: Exception) {
        null
    }

    fun openUrl(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
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

    fun openPersonalSettings(baseUrl: String = Product.API_BASE_URL): Boolean {
        openUrl("${baseUrl.trimEnd('/')}/login")
        return openUrl("${baseUrl.trimEnd('/')}/console/personal")
    }

    // --- Auth (access token) ----------------------------------------------

    /** Password login → access token + user profile. */
    suspend fun passwordLogin(
        username: String,
        password: String,
        baseUrl: String = Product.API_BASE_URL
    ): Result<Session> = try {
        val response = HttpClient.client.post("${baseUrl.trimEnd('/')}/api/user/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":${jsonString(username)},"password":${jsonString(password)}}""")
        }
        val root = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "Login failed"))
        } else {
            val data = root.getAsJsonObject("data")
                ?: return Result.failure(IllegalStateException("Login response missing data"))
            if (data.bool("require_2fa")) {
                Result.failure(IllegalStateException("2FA required — use /login access <token> from console personal settings"))
            } else {
                val access = data.str("access_token") ?: data.str("token")
                    ?: return Result.failure(IllegalStateException("Login missing access token"))
                val user = data.getAsJsonObject("user")
                val userId = user?.get("id")?.asInt
                    ?: return Result.failure(IllegalStateException("Login missing user id"))
                // Prefer durable PAT: regenerate via /api/user/token when session JWT was returned
                val durable = ensureDurableAccessToken(access, userId, baseUrl) ?: access
                fetchSelf(durable, userId, baseUrl).recoverCatching {
                    Session(durable, userId, user.str("username"), group = user.str("group"))
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Validate an access token against `/api/user/self`.
     * Newer NewAPI may not require New-Api-User; we still send it when known.
     */
    suspend fun fetchSelf(
        accessToken: String,
        userId: Int? = null,
        baseUrl: String = Product.API_BASE_URL
    ): Result<Session> = try {
        val root = authedGet("/api/user/self", accessToken, userId, baseUrl)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "Invalid access token"))
        } else {
            val d = root.getAsJsonObject("data")
                ?: return Result.failure(IllegalStateException("Empty /api/user/self"))
            val id = d.get("id")?.asInt ?: userId
                ?: return Result.failure(IllegalStateException("Missing user id"))
            Result.success(
                Session(
                    accessToken = accessToken,
                    userId = id,
                    username = d.str("username"),
                    displayName = d.str("display_name"),
                    quota = d.get("quota")?.asLong ?: 0,
                    usedQuota = d.get("used_quota")?.asLong ?: 0,
                    group = d.str("group"),
                    requestCount = d.get("request_count")?.asLong ?: 0
                )
            )
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Persist access token session and auto-claim relay sk- (never shown as a paste step).
     */
    suspend fun activateSession(
        session: Session,
        baseUrl: String = Product.API_BASE_URL
    ): Result<RelayToken> {
        val prev = ConfigManager.getGlobalConfig()
        ConfigManager.saveGlobalConfig(
            prev.copy(
                baseUrl = baseUrl,
                apiUrl = baseUrl,
                apiType = "chat",
                accessToken = session.accessToken,
                userId = session.userId,
                username = session.username,
                model = prev.model ?: Product.DEFAULT_MODEL
            )
        )
        ApiConfig.reload()
        return ensureRelayToken(forceRefresh = false).onSuccess { relay ->
            val cfg = ConfigManager.getGlobalConfig()
            ConfigManager.saveGlobalConfig(cfg.copy(apiKey = relay.key, relayTokenId = relay.id))
            ApiConfig.reload()
        }
    }

    /**
     * Ensure a named unlimited relay token exists; create if needed; return full key.
     * Called after login and before chat when apiKey is missing/invalid.
     */
    suspend fun ensureRelayToken(
        forceRefresh: Boolean = false,
        name: String = RELAY_TOKEN_NAME
    ): Result<RelayToken> {
        val cfg = ConfigManager.getGlobalConfig()
        val access = cfg.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No access token — run /login first"))
        val userId = cfg.userId
        val base = (cfg.baseUrl ?: cfg.apiUrl ?: Product.API_BASE_URL).trimEnd('/')

        if (!forceRefresh) {
            val existingKey = cfg.apiKey?.takeIf { it.isNotBlank() }
            val existingId = cfg.relayTokenId
            if (existingKey != null && validateRelayKey(existingKey, base) == null) {
                return Result.success(
                    RelayToken(existingId ?: 0, existingKey, name)
                )
            }
        }

        // Reuse token named Hlool Agent if present
        val listed = listTokens(access, userId, base).getOrElse { return Result.failure(it) }
        val mine = listed.firstOrNull { it.name == name }
        if (mine != null) {
            val key = fetchTokenKey(access, userId, mine.id, base).getOrElse { return Result.failure(it) }
            return Result.success(RelayToken(mine.id, key, mine.name, mine.group, mine.status))
        }

        // Create new unlimited token
        val createRoot = authedPost(
            "/api/token/",
            access,
            userId,
            base,
            """{"name":${jsonString(name)},"remain_quota":0,"unlimited_quota":true,"expired_time":-1}"""
        )
        if (createRoot.get("success")?.asBoolean != true) {
            return Result.failure(IllegalStateException(createRoot.str("message") ?: "Create token failed"))
        }
        val after = listTokens(access, userId, base).getOrElse { return Result.failure(it) }
        val created = after.firstOrNull { it.name == name }
            ?: after.firstOrNull()
            ?: return Result.failure(IllegalStateException("Token created but not found in list"))
        val key = fetchTokenKey(access, userId, created.id, base).getOrElse { return Result.failure(it) }
        return Result.success(RelayToken(created.id, key, created.name, created.group, created.status))
    }

    /** Before chat: if we have access token, make sure relay sk is ready. */
    suspend fun prepareForChat(): String? {
        val cfg = ConfigManager.getGlobalConfig()
        if (cfg.accessToken.isNullOrBlank()) {
            return if (cfg.apiKey.isNullOrBlank()) {
                "Not logged in. Run /login (access token) — sk- keys are claimed automatically."
            } else null
        }
        val result = ensureRelayToken(forceRefresh = false)
        val relay = result.getOrElse { return it.message }
        val cfg2 = ConfigManager.getGlobalConfig()
        if (cfg2.apiKey != relay.key || cfg2.relayTokenId != relay.id) {
            ConfigManager.saveGlobalConfig(cfg2.copy(apiKey = relay.key, relayTokenId = relay.id))
            ApiConfig.reload()
        }
        return null
    }

    // --- Management API (web console parity) ------------------------------

    suspend fun listModels(): Result<List<String>> = withAccess { access, userId, base ->
        val root = authedGet("/api/user/models", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            return@withAccess Result.failure(IllegalStateException(root.str("message") ?: "models failed"))
        }
        val data = root.get("data")
        val names = mutableListOf<String>()
        when {
            data?.isJsonArray == true -> data.asJsonArray.forEach { el ->
                when {
                    el.isJsonPrimitive -> names += el.asString
                    el.isJsonObject -> names += (el.asJsonObject.str("id") ?: el.asJsonObject.str("model") ?: el.toString())
                }
            }
            data?.isJsonObject == true -> data.asJsonObject.keySet().forEach { names += it }
        }
        Result.success(names)
    }

    suspend fun listGroups(): Result<JsonObject> = withAccess { access, userId, base ->
        val root = authedGet("/api/user/self/groups", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "groups failed"))
        } else {
            Result.success(root.getAsJsonObject("data") ?: JsonObject())
        }
    }

    suspend fun listTokens(
        accessToken: String? = null,
        userId: Int? = null,
        baseUrl: String? = null,
        page: Int = 0,
        size: Int = 50
    ): Result<List<TokenRow>> {
        val cfg = ConfigManager.getGlobalConfig()
        val access = accessToken ?: cfg.accessToken
            ?: return Result.failure(IllegalStateException("No access token"))
        val uid = userId ?: cfg.userId
        val base = (baseUrl ?: cfg.baseUrl ?: Product.API_BASE_URL).trimEnd('/')
        val root = authedGet("/api/token/?p=$page&page_size=$size&size=$size", access, uid, base)
        if (root.get("success")?.asBoolean != true) {
            return Result.failure(IllegalStateException(root.str("message") ?: "token list failed"))
        }
        val items = root.getAsJsonObject("data")?.getAsJsonArray("items")
            ?: root.getAsJsonArray("data")
            ?: JsonArray()
        val rows = items.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asInt ?: return@mapNotNull null
            val rawKey = o.str("key").orEmpty()
            val masked = if (rawKey.startsWith("sk-")) rawKey else if (rawKey.isNotEmpty()) "sk-$rawKey" else ""
            TokenRow(
                id = id,
                name = o.str("name") ?: "",
                keyMasked = masked,
                status = o.get("status")?.asInt ?: 0,
                group = o.str("group") ?: "",
                remainQuota = o.get("remain_quota")?.asLong ?: 0,
                unlimitedQuota = o.bool("unlimited_quota"),
                expiredTime = o.get("expired_time")?.asLong ?: -1,
                usedQuota = o.get("used_quota")?.asLong ?: 0
            )
        }
        return Result.success(rows)
    }

    suspend fun createToken(
        name: String,
        group: String? = null,
        unlimited: Boolean = true,
        remainQuotaDollars: Double? = null
    ): Result<Int> = withAccess { access, userId, base ->
        val remain = if (unlimited) 0L else ((remainQuotaDollars ?: 0.0) * quotaPerUnit()).toLong()
        val body = buildString {
            append("{")
            append("\"name\":${jsonString(name)}")
            append(",\"unlimited_quota\":$unlimited")
            append(",\"remain_quota\":$remain")
            append(",\"expired_time\":-1")
            if (!group.isNullOrBlank()) append(",\"group\":${jsonString(group)}")
            append("}")
        }
        val root = authedPost("/api/token/", access, userId, base, body)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "create failed"))
        } else {
            val listed = listTokens(access, userId, base).getOrElse { return@withAccess Result.failure(it) }
            val id = listed.firstOrNull { it.name == name }?.id
                ?: return@withAccess Result.failure(IllegalStateException("Created but id unknown"))
            Result.success(id)
        }
    }

    suspend fun updateTokenGroup(tokenId: Int, group: String): Result<Unit> = withAccess { access, userId, base ->
        val detail = authedGet("/api/token/$tokenId", access, userId, base)
        if (detail.get("success")?.asBoolean != true) {
            return@withAccess Result.failure(IllegalStateException(detail.str("message") ?: "token not found"))
        }
        val t = detail.getAsJsonObject("data")
            ?: return@withAccess Result.failure(IllegalStateException("empty token"))
        t.addProperty("group", group)
        t.addProperty("id", tokenId)
        // Drop masked key from update payload if present
        t.remove("key")
        val root = authedPut("/api/token/", access, userId, base, t.toString())
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "update failed"))
        } else Result.success(Unit)
    }

    suspend fun setTokenStatus(tokenId: Int, enabled: Boolean): Result<Unit> = withAccess { access, userId, base ->
        val status = if (enabled) 1 else 2
        val root = authedPut(
            "/api/token/?status_only=true",
            access,
            userId,
            base,
            """{"id":$tokenId,"status":$status}"""
        )
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "status update failed"))
        } else Result.success(Unit)
    }

    suspend fun deleteToken(tokenId: Int): Result<Unit> = withAccess { access, userId, base ->
        val root = authedDelete("/api/token/$tokenId/", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "delete failed"))
        } else Result.success(Unit)
    }

    suspend fun listLogs(page: Int = 0, size: Int = 20, modelName: String? = null): Result<List<LogRow>> =
        withAccess { access, userId, base ->
            val q = buildString {
                append("/api/log/?p=$page&page_size=$size&size=$size")
                if (!modelName.isNullOrBlank()) append("&model_name=${encode(modelName)}")
            }
            val root = authedGet(q, access, userId, base)
            if (root.get("success")?.asBoolean != true) {
                return@withAccess Result.failure(IllegalStateException(root.str("message") ?: "logs failed"))
            }
            val items = root.getAsJsonObject("data")?.getAsJsonArray("items")
                ?: root.getAsJsonArray("data")
                ?: JsonArray()
            Result.success(items.mapNotNull { el ->
                val o = el.asJsonObject
                LogRow(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    modelName = o.str("model_name") ?: "",
                    tokenName = o.str("token_name") ?: "",
                    quota = o.get("quota")?.asLong ?: 0,
                    promptTokens = o.get("prompt_tokens")?.asInt ?: 0,
                    completionTokens = o.get("completion_tokens")?.asInt ?: 0,
                    content = o.str("content") ?: "",
                    createdAt = o.get("created_at")?.asLong ?: 0,
                    type = o.get("type")?.asInt ?: 0
                )
            })
        }

    suspend fun topupInfo(): Result<JsonObject> = withAccess { access, userId, base ->
        val root = authedGet("/api/user/topup/info", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "topup info failed"))
        } else Result.success(root.getAsJsonObject("data") ?: JsonObject())
    }

    suspend fun topupHistory(page: Int = 0, size: Int = 20): Result<JsonElement> = withAccess { access, userId, base ->
        val root = authedGet("/api/user/topup/self?p=$page&page_size=$size", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "topup history failed"))
        } else Result.success(root.get("data") ?: JsonObject())
    }

    suspend fun notice(): Result<String> = try {
        val root = getJson("${Product.API_BASE_URL.trimEnd('/')}/api/notice")
        Result.success(root.str("data") ?: "")
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun pricingSummary(): Result<JsonObject> = try {
        val root = getJson("${Product.API_BASE_URL.trimEnd('/')}/api/pricing")
        Result.success(root)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateSelf(payload: String): Result<Unit> = withAccess { access, userId, base ->
        val root = authedPut("/api/user/self", access, userId, base, payload)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "update self failed"))
        } else Result.success(Unit)
    }

    suspend fun affCode(): Result<String> = withAccess { access, userId, base ->
        val root = authedGet("/api/user/aff", access, userId, base)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "aff failed"))
        } else {
            val data = root.get("data")
            Result.success(
                when {
                    data == null || data.isJsonNull -> ""
                    data.isJsonPrimitive -> data.asString
                    data.isJsonObject -> data.asJsonObject.str("aff_code") ?: data.toString()
                    else -> data.toString()
                }
            )
        }
    }

    fun formatQuota(raw: Long, quotaPerUnit: Double = quotaPerUnit()): String {
        val dollars = raw / quotaPerUnit
        return String.format("$%.4f (%d)", dollars, raw)
    }

    fun quotaPerUnit(): Double {
        val cfg = ConfigManager.getGlobalConfig()
        // Prefer last known from status; default NewAPI 500000
        return 500_000.0
    }

    // --- Internals --------------------------------------------------------

    private suspend fun ensureDurableAccessToken(
        access: String,
        userId: Int,
        baseUrl: String
    ): String? = try {
        // If already a PAT (works on /api/user/self), keep it; else mint one.
        val self = fetchSelf(access, userId, baseUrl)
        if (self.isSuccess) {
            // Try regenerate durable PAT for CLI persistence (login JWT expires)
            val root = authedGet("/api/user/token", access, userId, baseUrl)
            if (root.get("success")?.asBoolean == true) {
                val data = root.get("data")
                when {
                    data != null && data.isJsonPrimitive -> data.asString
                    data != null && data.isJsonObject -> data.asJsonObject.str("token") ?: data.asJsonObject.str("access_token")
                    else -> null
                } ?: access
            } else access
        } else null
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchTokenKey(
        access: String,
        userId: Int?,
        tokenId: Int,
        baseUrl: String
    ): Result<String> = try {
        val root = authedGet("/api/token/$tokenId/key", access, userId, baseUrl)
        if (root.get("success")?.asBoolean != true) {
            Result.failure(IllegalStateException(root.str("message") ?: "key fetch failed"))
        } else {
            val raw = root.getAsJsonObject("data")?.str("key")
                ?: return Result.failure(IllegalStateException("key missing"))
            Result.success(if (raw.startsWith("sk-")) raw else "sk-$raw")
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun validateRelayKey(apiKey: String, baseUrl: String): String? = try {
        val response = HttpClient.client.get("${baseUrl.trimEnd('/')}/v1/models") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
        if (response.status.isSuccess()) null
        else "invalid (${response.status.value})"
    } catch (e: Exception) {
        e.message
    }

    private suspend fun <T> withAccess(
        block: suspend (access: String, userId: Int?, base: String) -> Result<T>
    ): Result<T> {
        val cfg = ConfigManager.getGlobalConfig()
        val access = cfg.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No access token — run /login"))
        val base = (cfg.baseUrl ?: cfg.apiUrl ?: Product.API_BASE_URL).trimEnd('/')
        return block(access, cfg.userId, base)
    }

    private suspend fun authedGet(
        path: String,
        access: String,
        userId: Int?,
        baseUrl: String
    ): JsonObject {
        val response = HttpClient.client.get("${baseUrl.trimEnd('/')}$path") {
            header(HttpHeaders.Authorization, "Bearer $access")
            if (userId != null) header("New-Api-User", userId.toString())
        }
        return gson.fromJson(response.bodyAsText(), JsonObject::class.java)
    }

    private suspend fun authedPost(
        path: String,
        access: String,
        userId: Int?,
        baseUrl: String,
        body: String
    ): JsonObject {
        val response = HttpClient.client.post("${baseUrl.trimEnd('/')}$path") {
            header(HttpHeaders.Authorization, "Bearer $access")
            if (userId != null) header("New-Api-User", userId.toString())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return gson.fromJson(response.bodyAsText(), JsonObject::class.java)
    }

    private suspend fun authedPut(
        path: String,
        access: String,
        userId: Int?,
        baseUrl: String,
        body: String
    ): JsonObject {
        val response = HttpClient.client.put("${baseUrl.trimEnd('/')}$path") {
            header(HttpHeaders.Authorization, "Bearer $access")
            if (userId != null) header("New-Api-User", userId.toString())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return gson.fromJson(response.bodyAsText(), JsonObject::class.java)
    }

    private suspend fun authedDelete(
        path: String,
        access: String,
        userId: Int?,
        baseUrl: String
    ): JsonObject {
        val response = HttpClient.client.delete("${baseUrl.trimEnd('/')}$path") {
            header(HttpHeaders.Authorization, "Bearer $access")
            if (userId != null) header("New-Api-User", userId.toString())
        }
        return gson.fromJson(response.bodyAsText(), JsonObject::class.java)
    }

    private suspend fun getJson(url: String): JsonObject {
        val text = HttpClient.client.get(url).bodyAsText()
        return gson.fromJson(text, JsonObject::class.java)
    }

    private fun jsonString(value: String): String = gson.toJson(value)
    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
}
