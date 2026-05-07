package rj.cocacode.platform

actual fun getPlatformName(): String = "MinGW"

actual fun currentTimeMillis(): Long {
    // Use Kotlin/Native stdlib time source which works on all native targets
    return kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
}
