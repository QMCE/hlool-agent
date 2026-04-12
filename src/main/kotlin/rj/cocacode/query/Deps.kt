package rj.cocacode.query

import java.util.UUID

data class QueryDeps(
  val callModel: suspend (String) -> String,
  val microcompact: suspend (List<String>) -> List<String>,
  val autocompact: suspend (List<String>) -> List<String>,
  val uuid: () -> String
)

fun productionDeps(): QueryDeps {
  return QueryDeps(
    callModel = ::queryModelWithStreaming,
    microcompact = ::microcompactMessages,
    autocompact = ::autoCompactIfNeeded,
    uuid = { UUID.randomUUID().toString() }
  )
}

suspend fun queryModelWithStreaming(input: String): String {
  return input
}

suspend fun microcompactMessages(messages: List<String>): List<String> {
  return messages
}

suspend fun autoCompactIfNeeded(messages: List<String>): List<String> {
  return messages
}
