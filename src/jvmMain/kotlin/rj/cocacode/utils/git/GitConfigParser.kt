package rj.cocacode.utils.git

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

suspend fun parseGitConfigValue(
  gitDir: String,
  section: String,
  subsection: String?,
  key: String,
): String? {
  return try {
    val configPath = Paths.get(gitDir).resolve("config")
    val config = Files.readString(configPath, StandardCharsets.UTF_8)
    parseConfigString(config, section, subsection, key)
  } catch (_: Exception) {
    null
  }
}

fun parseConfigString(
  config: String,
  section: String,
  subsection: String?,
  key: String,
): String? {
  val lines = config.split('\n')
  val sectionLower = section.lowercase()
  val keyLower = key.lowercase()

  var inSection = false
  for (line in lines) {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith(';')) {
      continue
    }
    if (trimmed.startsWith('[')) {
      inSection = matchesSectionHeader(trimmed, sectionLower, subsection)
      continue
    }
    if (!inSection) continue

    val parsed = parseKeyValue(trimmed) ?: continue
    if (parsed.first.lowercase() == keyLower) {
      return parsed.second
    }
  }
  return null
}

private fun parseKeyValue(line: String): Pair<String, String>? {
  var i = 0
  while (i < line.length && isKeyChar(line[i])) {
    i++
  }
  if (i == 0) return null
  val key = line.substring(0, i)

  while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
    i++
  }
  if (i >= line.length || line[i] != '=') return null
  i++
  while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
    i++
  }
  val value = parseValue(line, i)
  return Pair(key, value)
}

private fun parseValue(line: String, start: Int): String {
  var result = StringBuilder()
  var inQuote = false
  var i = start
  while (i < line.length) {
    val ch = line[i]
    if (!inQuote && (ch == '#' || ch == ';')) {
      break
    }
    if (ch == '"') {
      inQuote = !inQuote
      i++
      continue
    }
    if (ch == '\\' && i + 1 < line.length) {
      val next = line[i + 1]
      if (inQuote) {
        when (next) {
          'n' -> { result.append('\n') }
          't' -> { result.append('\t') }
          'b' -> { result.append('\b') }
          '"' -> { result.append('"') }
          '\\' -> { result.append('\\') }
          else -> { result.append(next) }
        }
        i += 2
        continue
      }
      if (next == '\\') {
        result.append('\\')
        i += 2
        continue
      }
    }
    result.append(ch)
    i++
  }
  if (!inQuote) {
    return trimTrailingWhitespace(result.toString())
  }
  return result.toString()
}

private fun trimTrailingWhitespace(s: String): String {
  var end = s.length
  while (end > 0 && (s[end - 1] == ' ' || s[end - 1] == '\t')) end--
  return s.substring(0, end)
}

private fun matchesSectionHeader(line: String, sectionLower: String, subsection: String?): Boolean {
  var i = 1
  while (i < line.length && line[i] != ']' && line[i] != ' ' && line[i] != '\t' && line[i] != '"') {
    i++
  }
  val foundSection = line.substring(1, i).lowercase()
  if (foundSection != sectionLower) return false
  if (subsection == null) {
    return i < line.length && line[i] == ']'
  }
  while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
  if (i >= line.length || line[i] != '"') return false
  i++
  var foundSubsection = StringBuilder()
  while (i < line.length && line[i] != '"') {
    val c = line[i]
    if (c == '\\' && i + 1 < line.length) {
      val next = line[i + 1]
      if (next == '\\' || next == '"') {
        foundSubsection.append(next)
        i += 2
        continue
      }
      foundSubsection.append(next)
      i += 2
      continue
    }
    foundSubsection.append(c)
    i++
  }
  if (i >= line.length || line[i] != '"') return false
  i++
  if (i >= line.length || line[i] != ']') return false
  return foundSubsection.toString() == subsection
}

private fun isKeyChar(ch: Char): Boolean {
  return (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '-'
}
