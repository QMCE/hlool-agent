package rj.cocacode.remote

import kotlin.collections.HashMap

// Lightweight type aliases to mirror TS-lean structures
typealias SDKMessage = Map<String, Any?>
typealias SDKControlRequest = Map<String, Any?>
typealias SDKControlPermissionRequest = Map<String, Any?>
typealias SDKControlResponse = Map<String, Any?>
typealias SDKControlCancelRequest = Map<String, Any?>
typealias RemoteMessageContent = Map<String, Any?>

data class RemoteSessionConfig(
  val sessionId: String,
  val getAccessToken: () -> String,
  val orgUuid: String,
  val hasInitialPrompt: Boolean = false,
  val viewerOnly: Boolean = false,
) 

data class RemotePermissionResponse(
  val behavior: String,
  val updatedInput: Map<String, Any?>? = null,
  val message: String? = null,
) 

data class RemoteSessionCallbacks(
  val onMessage: (SDKMessage) -> Unit,
  val onPermissionRequest: (SDKControlRequest, String) -> Unit,
  val onPermissionCancelled: ((String, String?) -> Unit)? = null,
  val onConnected: (() -> Unit)? = null,
  val onDisconnected: (() -> Unit)? = null,
  val onReconnecting: (() -> Unit)? = null,
  val onError: ((Throwable) -> Unit)? = null,
)

class RemoteSessionManager(
  private val config: RemoteSessionConfig,
  private val callbacks: RemoteSessionCallbacks,
) {
  private var websocket: SessionsWebSocket? = null
private val pendingPermissionRequests: MutableMap<String, Map<String, Any?>> = mutableMapOf()

  

  fun connect(): Unit {
    @Suppress("UNCHECKED_CAST")
    val wsCallbacks = SessionsWebSocketCallbacks(
      onMessage = { message -> handleMessage(message as SDKMessage) },
      onConnected = { callbacks.onConnected?.invoke() },
      onClose = { callbacks.onDisconnected?.invoke() },
      onReconnecting = { callbacks.onReconnecting?.invoke() },
      onError = { err -> callbacks.onError?.invoke(err) },
    )

    websocket = SessionsWebSocket(
      config.sessionId,
      config.orgUuid,
      config.getAccessToken,
      wsCallbacks,
    )

    // In real implementation this would be a suspend call to connect
    // Here we just invoke a placeholder to reflect behavior in TS
    //noinspection insertSuspendFunction
    websocket?.connect()
  }

  private fun handleMessage(message: SDKMessage) {
    val type = message["type"] as? String
    when (type) {
      "control_request" -> handleControlRequest(message)
      "control_cancel_request" -> {
        val requestId = message["request_id"] as? String ?: return
        val pending = pendingPermissionRequests.remove(requestId)
        callbacks.onPermissionCancelled?.invoke(requestId, pending?.get("tool_use_id") as? String)
      }
      "control_response" -> {
        // noop for now
      }
      else -> callbacks.onMessage(message)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun handleControlRequest(message: SDKMessage) {
    val requestId = message["request_id"] as? String ?: return
    val inner = message["request"] as? Map<String, Any?> ?: emptyMap()
    val subtype = inner["subtype"] as? String ?: return
    if (subtype == "can_use_tool") {
      val permissionRequest: SDKControlPermissionRequest = mapOf(
        "type" to "control_request",
        "request_id" to requestId,
        "tool_name" to (inner["tool_name"] as? String),
        "tool_use_id" to (inner["tool_use_id"] as? String),
        "input" to (inner["input"] as? Map<String, Any?>),
      )
      pendingPermissionRequests[requestId] = permissionRequest
      callbacks.onPermissionRequest(permissionRequest, requestId)
    } else {
      // Unsupported subtype: reply with error (kept minimal)
      val response = mapOf(
        "type" to "control_response",
        "response" to mapOf(
          "subtype" to "error",
          "request_id" to requestId,
          "error" to "Unsupported control request subtype: $subtype",
        )
      )
      websocket?.sendControlResponse(response)
    }
  }

  suspend fun sendMessage(content: RemoteMessageContent, opts: Map<String, Any?>? = null): Boolean {
    // Placeholder: pretend to POST toCCR and return success
    return true
  }

  fun respondToPermissionRequest(requestId: String, result: RemotePermissionResponse) {
    val pending = pendingPermissionRequests.remove(requestId) ?: return
    val innerResponse = mutableMapOf<String, Any?>()
    innerResponse["behavior"] = result.behavior
    if (result.behavior == "allow") {
      innerResponse["updatedInput"] = result.updatedInput ?: emptyMap<String, Any?>()
    } else {
      innerResponse["message"] = result.message ?: ""
    }
    val response = mapOf(
      "type" to "control_response",
      "response" to mapOf(
        "subtype" to if (result.behavior == "allow") "success" else "error",
        "request_id" to requestId,
        "response" to innerResponse,
      )
    )
    websocket?.sendControlResponse(response)
  }

  fun isConnected(): Boolean {
    return websocket?.isConnected() ?: false
  }

  fun cancelSession(): Unit {
    websocket?.sendControlRequest(mapOf("subtype" to "interrupt"))
  }

  fun getSessionId(): String = config.sessionId

  fun disconnect(): Unit {
    websocket?.close()
    websocket = null
    pendingPermissionRequests.clear()
  }

  fun reconnect(): Unit {
    websocket?.reconnect()
  }
}

// Factory helper mirroring TS signature
fun createRemoteSessionConfig(
  sessionId: String,
  getAccessToken: () -> String,
  orgUuid: String,
  hasInitialPrompt: Boolean = false,
  viewerOnly: Boolean = false,
): RemoteSessionConfig {
  return RemoteSessionConfig(sessionId, getAccessToken, orgUuid, hasInitialPrompt, viewerOnly)
}
