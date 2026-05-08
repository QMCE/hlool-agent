package rj.cocacode.services.remoteManagedSettings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RemoteManagedSettingsResponse(
    val uuid: String,
    val checksum: String,
    val settings: Map<String, JsonElement> = emptyMap()
)

data class RemoteManagedSettingsFetchResult(
    val success: Boolean,
    val settings: Map<String, Any?>? = null,
    val checksum: String? = null,
    val error: String? = null,
    val skipRetry: Boolean = false
)