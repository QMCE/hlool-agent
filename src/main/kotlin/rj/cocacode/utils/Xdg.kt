package rj.cocacode.utils

import java.io.File

data class XDGOptions(val env: Map<String, String?>? = null, val homedir: String? = null)

private fun resolveOptions(options: XDGOptions?): Pair<Map<String, String?>, String> {
  val env = options?.env ?: System.getenv().entries.associate { it.key to it.value }
  val home = options?.homedir ?: System.getenv("HOME") ?: System.getProperty("user.home") ?: ""
  return Pair(env, home)
}

suspend fun getXDGStateHome(options: XDGOptions? = null): String {
  val (env, home) = resolveOptions(options)
  return env["XDG_STATE_HOME"] ?: File(home, ".local/state").path
}

suspend fun getXDGCacheHome(options: XDGOptions? = null): String {
  val (env, home) = resolveOptions(options)
  return env["XDG_CACHE_HOME"] ?: File(home, ".cache").path
}

suspend fun getXDGDataHome(options: XDGOptions? = null): String {
  val (env, home) = resolveOptions(options)
  return env["XDG_DATA_HOME"] ?: File(home, ".local/share").path
}

suspend fun getUserBinDir(options: XDGOptions? = null): String {
  val (env, home) = resolveOptions(options)
  return File(home, ".local/bin").path
}
