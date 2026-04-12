package rj.cocacode.query

import java.lang.System.getenv

data class QueryConfig(
  val sessionId: String,
  val gates: Gates,
)

data class Gates(
  val streamingToolExecution: Boolean,
  val emitToolUseSummaries: Boolean,
  val isAnt: Boolean,
  val fastModeEnabled: Boolean,
)

fun buildQueryConfig(): QueryConfig {
  return QueryConfig(
    sessionId = getSessionId(),
    gates = Gates(
      streamingToolExecution = checkStatsigFeatureGateCached("tengu_streaming_tool_execution2"),
      emitToolUseSummaries = isEnvTruthy(getenv("COCACODE_EMIT_TOOL_USE_SUMMARIES")
          ?: getenv("CLAUDE_CODE_EMIT_TOOL_USE_SUMMARIES")),
      isAnt = getenv("USER_TYPE") == "ant",
      fastModeEnabled = !isEnvTruthy(getenv("COCACODE_DISABLE_FAST_MODE")
          ?: getenv("CLAUDE_CODE_DISABLE_FAST_MODE")),
    ),
  )
}

private fun getSessionId(): String {
  return "sess_" + System.currentTimeMillis()
}

private fun checkStatsigFeatureGateCached(feature: String): Boolean {
  return false
}

private fun isEnvTruthy(value: String?): Boolean {
  return value?.equals("true", ignoreCase = true) ?: false
}
