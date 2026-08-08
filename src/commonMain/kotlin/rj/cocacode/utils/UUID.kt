package rj.cocacode.utils

/**
 * Generates a random UUID string. Expected in commonMain, actuals provided per
 * platform (JVM: java.util.UUID, native: platform API).
 */
expect fun generateUuid(): String