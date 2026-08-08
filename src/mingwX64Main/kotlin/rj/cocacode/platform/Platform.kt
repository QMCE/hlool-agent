package rj.cocacode.platform

actual fun getPlatformName(): String = "MinGW"

actual fun currentTimeMillis(): Long {
    // Use the stable Kotlin clock, which returns real epoch milliseconds on native.
    return kotlin.time.Clock.System.now().toEpochMilliseconds()
}
