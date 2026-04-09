package rj.cocacode.utils

import java.security.SecureRandom

private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

fun validateUuid(maybeUuid: Any?): String? {
    if (maybeUuid !is String) return null
    return if (UUID_REGEX.matches(maybeUuid)) maybeUuid else null
}

fun createAgentId(label: String? = null): String {
    val suffixBytes = ByteArray(8)
    SecureRandom().nextBytes(suffixBytes)
    val suffix = suffixBytes.joinToString("") { "%02x".format(it) }
    return if (!label.isNullOrEmpty()) "a${label}-${suffix}" else "a${suffix}"
}

fun generateUuid(): String = java.util.UUID.randomUUID().toString()
