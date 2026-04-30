package rj.cocacode.utils

import java.util.UUID

/**
 * Generate a UUID string.
 */
fun generateUuid(): String = UUID.randomUUID().toString()
