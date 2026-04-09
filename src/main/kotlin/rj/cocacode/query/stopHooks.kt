package rj.cocacode.query

data class StopHookResult(
  val blockingErrors: List<Any>,
  val preventContinuation: Boolean,
)

suspend fun handleStopHooks(
  messagesForQuery: List<Any>,
  assistantMessages: List<Any>,
  systemPrompt: Any,
  userContext: Map<String, String>,
  systemContext: Map<String, String>,
  toolUseContext: Any,
  querySource: String,
  stopHookActive: Boolean = false
): StopHookResult {
  return StopHookResult(blockingErrors = emptyList(), preventContinuation = false)
}
