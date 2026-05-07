package rj.cocacode.platform

actual fun getPlatformName(): String = "JVM"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
