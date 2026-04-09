package rj.cocacode.services.remoteManagedSettings

import kotlinx.serialization.Serializable

@Serializable
data class RemoteManagedSettingsResponse(
    val uuid: String,
    val checksum: String,
    val settings: Map<String, Any?> = emptyMap()
)

data class RemoteManagedSettingsFetchResult(
    val success: Boolean,
    val settings: Map<String, Any?>? = null,
    val checksum: String? = null,
    val error: String? = null,
    val skipRetry: Boolean = false
)