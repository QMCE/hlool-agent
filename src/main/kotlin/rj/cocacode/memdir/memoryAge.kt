package rj.cocacode.memdir

import java.util.Date
import java.time.Instant

fun memoryAgeDays(mtimeMs: Long): Int {
  val delta = System.currentTimeMillis() - mtimeMs
  val days = delta / 86_400_000L
  return if (days < 0) 0 else days.toInt()
}

fun memoryAge(mtimeMs: Long): String {
  val d = memoryAgeDays(mtimeMs)
  return when (d) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$d days ago"
  }
}

fun memoryFreshnessText(mtimeMs: Long): String {
  val d = memoryAgeDays(mtimeMs)
  if (d <= 1) return ""
  return "This memory is $d days old. Memories are point-in-time observations, not live state — claims about code behavior or file:line citations may be outdated. Verify against current code before asserting as fact."
}

fun memoryFreshnessNote(mtimeMs: Long): String {
  val text = memoryFreshnessText(mtimeMs)
  if (text.isEmpty()) return ""
  return "<system-reminder>$text</system-reminder>\n"
}
