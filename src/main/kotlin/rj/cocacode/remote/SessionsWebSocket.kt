package rj.cocacode.remote

data class SessionsWebSocketCallbacks(
  val onMessage: (Any) -> Unit,
  val onClose: (() -> Unit)? = null,
  val onError: ((Throwable) -> Unit)? = null,
  val onConnected: (() -> Unit)? = null,
  val onReconnecting: (() -> Unit)? = null,
)

class SessionsWebSocket(
  private val sessionId: String,
  private val orgUuid: String,
  private val getAccessToken: () -> String,
  private val callbacks: SessionsWebSocketCallbacks,
) {
  private var state: String = "closed"
  private var ws: Any? = null

  fun connect(): Unit {
    if (state == "connecting") return
    state = "connecting"
    state = "connected"
    callbacks.onConnected?.invoke()
  }

  fun handleMessage(data: String) {
    val payload: MutableMap<String, Any?> = mutableMapOf("type" to "unknown", "data" to data)
    callbacks.onMessage(payload)
  }

  fun close(): Unit {
    state = "closed"
    ws = null
    callbacks.onClose?.invoke()
  }

  fun reconnect(): Unit {
    if (state == "connected") return
    state = "connecting"
    state = "connected"
    callbacks.onConnected?.invoke()
  }

  fun isConnected(): Boolean = state == "connected"

  fun sendControlResponse(response: Map<String, Any?>) {
    if (!isConnected()) return
    val json = jsonStringify(response)
    
  }

  fun sendControlRequest(request: Map<String, Any?>) {
    if (!isConnected()) return
    val json = jsonStringify(request)
    _log("Sending control request: $json")
  }

  private fun _log(msg: String) {
    println(msg)
  }

  private fun jsonStringify(value: Any?): String {
    return when (value) {
      null -> "null"
      is String -> "\"${escape(value)}\""
      is Number, is Boolean -> value.toString()
      is Map<*, *> -> {
        val entries = value.entries.map {
          val k = it.key as? String ?: it.key.toString()
          val v = jsonStringify(it.value)
          "\"${escape(k)}\":$v"
        }
        "{" + entries.joinToString(",") + "}"
      }
      is Iterable<*> -> {
        val items = value.map { jsonStringify(it) }
        "[" + items.joinToString(",") + "]"
      }
      else -> "\"${escape(value.toString())}\""
    }
  }

  private fun escape(s: String): String {
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
  }
}
