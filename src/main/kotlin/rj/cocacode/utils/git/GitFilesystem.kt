package rj.cocacode.utils.git

import java.nio.file.Files
import java.nio.file.Path

sealed class GitHead {
  data class Branch(val name: String) : GitHead()
  data class Detached(val sha: String) : GitHead()
}

private fun getCwdInternal(): String = System.getProperty("user.dir")

suspend fun resolveGitDir(startPath: String? = null): String? {
  val cwd = Path.of(startPath ?: getCwdInternal()).toAbsolutePath().normalize()
  val root = findGitRoot(cwd) ?: return null
  val gitPath = root.resolve(".git")
  return try {
    if (Files.isRegularFile(gitPath)) {
      val content = Files.readString(gitPath).trim()
      if (content.startsWith("gitdir:")) {
        val rawDir = content.substring("gitdir:".length).trim()
        root.resolve(rawDir).normalize().toString()
      } else {
        gitPath.toString()
      }
    } else {
      gitPath.toString()
    }
  } catch (_: Exception) {
    null
  }
}

private fun findGitRoot(start: Path): Path? {
  var p: Path? = start
  while (p != null) {
    val gitPath = p.resolve(".git")
    if (Files.exists(gitPath)) return p
    p = p.parent
  }
  return null
}

fun isSafeRefName(name: String): Boolean {
  if (name.isEmpty() || name.startsWith('-') || name.startsWith('/')) return false
  if (name.contains("..")) return false
  val segments = name.split('/')
  if (segments.any { it == "." || it.isEmpty() }) return false
  val r = Regex("""^[a-zA-Z0-9/._+@-]+$""")
  return r.matches(name)
}

fun isValidGitSha(s: String): Boolean {
  return s.matches(Regex("^[0-9a-f]{40}$")) || s.matches(Regex("^[0-9a-f]{64}$"))
}

suspend fun readGitHead(gitDir: String): GitHead? {
  return try {
    val headPath = Path.of(gitDir).resolve("HEAD")
    val content = Files.readString(headPath).trim()
    if (content.startsWith("ref:")) {
      val ref = content.substring("ref:".length).trim()
      if (ref.startsWith("refs/heads/")) {
        val name = ref.substring("refs/heads/".length)
        if (!isSafeRefName(name)) return null
        GitHead.Branch(name)
      } else {
        if (!isSafeRefName(ref)) return null
        val sha = resolveRef(gitDir, ref)
        GitHead.Detached(sha ?: "")
      }
    } else {
      if (!isValidGitSha(content)) return null
      GitHead.Detached(content)
    }
  } catch (_: Exception) {
    null
  }
}

suspend fun resolveRef(gitDir: String, ref: String): String? {
  val direct = resolveRefInDir(gitDir, ref)
  if (direct != null) return direct
  val commonDir = getCommonDir(gitDir)
  if (commonDir != null && commonDir != gitDir) {
    return resolveRefInDir(commonDir, ref)
  }
  return null
}

private suspend fun resolveRefInDir(dir: String, ref: String): String? {
  try {
    val p = Path.of(dir).resolve(ref)
    val content = Files.readString(p).trim()
    if (content.startsWith("ref:")) {
      val target = content.substring("ref:".length).trim()
      if (!isSafeRefName(target)) return null
      return resolveRef(dir, target)
    }
    if (content.isNotEmpty() && content.matches(Regex("^[0-9a-f]{40}$")) || content.matches(Regex("^[0-9a-f]{64}$"))) return content
  } catch (_: Exception) {
    // ignore and try packed-refs
  }

  try {
    val packed = Files.readString(Path.of(dir).resolve("packed-refs"))
    for (line in packed.split('\n')) {
      if (line.startsWith("#") || line.startsWith('^')) continue
      val spaceIdx = line.indexOf(' ')
      if (spaceIdx == -1) continue
      val sha = line.substring(0, spaceIdx)
      val refName = line.substring(spaceIdx + 1)
      if (refName == ref && isValidGitSha(sha)) return sha
    }
  } catch (_: Exception) {
    // No packed-refs
  }
  return null
}

suspend fun getCommonDir(gitDir: String): String? {
  return try {
    val communDirPath = Path.of(gitDir).resolve("commondir")
    val content = Files.readString(communDirPath).trim()
    Path.of(gitDir).resolve(content).normalize().toString()
  } catch (_: Exception) {
    null
  }
}

suspend fun readRawSymref(
  gitDir: String,
  refPath: String,
  branchPrefix: String,
): String? {
  return try {
    val content = Files.readString(Path.of(gitDir).resolve(refPath)).trim()
    if (content.startsWith("ref:")) {
      val target = content.substring("ref:".length).trim()
      if (target.startsWith(branchPrefix)) {
        val name = target.substring(branchPrefix.length)
        if (!isSafeRefName(name)) return null
        return name
      }
    }
    null
  } catch (_: Exception) {
    null
  }
}

class GitFileWatcher {
  private val cache = mutableMapOf<String, Pair<Boolean, Any?>>()
  @Suppress("UNCHECKED_CAST")
  suspend fun <T> get(key: String, compute: suspend () -> T): T {
    val existing = cache[key]?.second as? T
    if (existing != null) return existing
    val value = compute()
    cache[key] = Pair(false, value)
    return value
  }
  fun reset() { cache.clear() }
}

private val gitWatcher = GitFileWatcher()

suspend fun computeBranch(): String {
  val gitDir = resolveGitDir()
  if (gitDir == null) return "HEAD"
  val head = readGitHead(gitDir)
  if (head == null) return "HEAD"
  return if (head is GitHead.Branch) head.name else "HEAD"
}

suspend fun computeHead(): String {
  val gitDir = resolveGitDir()
  if (gitDir == null) return ""
  val head = readGitHead(gitDir)
  if (head == null) return ""
  return when (head) {
    is GitHead.Branch -> resolveRef(gitDir, "refs/heads/${head.name}") ?: ""
    is GitHead.Detached -> head.sha
  }
}

suspend fun computeRemoteUrl(): String? {
  val gitDir = resolveGitDir() ?: return null
  val url = parseGitConfigValue(gitDir, "remote", "origin", "url")
  if (url != null) return url
  val commonDir = getCommonDir(gitDir)
  if (commonDir != null && commonDir != gitDir) {
    return parseGitConfigValue(commonDir, "remote", "origin", "url")
  }
  return null
}

suspend fun computeDefaultBranch(): String {
  val gitDir = resolveGitDir() ?: return "main"
  val commonDir = getCommonDir(gitDir) ?: gitDir
  val branchFromSymref = readRawSymref(
    commonDir,
    "refs/remotes/origin/HEAD",
    "refs/remotes/origin/",
  )
  if (branchFromSymref != null) return branchFromSymref
  for (candidate in listOf("main", "master")) {
    val sha = resolveRef(commonDir, "refs/remotes/origin/$candidate")
    if (sha != null) return candidate
  }
  return "main"
}

suspend fun getHeadForDir(cwd: String): String? {
  val gitDir = resolveGitDir(cwd) ?: return null
  val head = readGitHead(gitDir) ?: return null
  return when (head) {
    is GitHead.Branch -> resolveRef(gitDir, "refs/heads/${head.name}")
    is GitHead.Detached -> head.sha
  }
}

suspend fun readWorktreeHeadSha(worktreePath: String): String? {
  return try {
    val ptr = Files.readString(Path.of(worktreePath, ".git")).trim()
    if (!ptr.startsWith("gitdir:")) return null
    val gitDir = Path.of(worktreePath).resolve(ptr.substring("gitdir:".length).trim()).normalize().toString()
    val head = readGitHead(gitDir) ?: return null
    when (head) {
      is GitHead.Branch -> resolveRef(gitDir, "refs/heads/${head.name}")
      is GitHead.Detached -> head.sha
    }
  } catch (_: Exception) {
    null
  }
}

suspend fun getRemoteUrlForDir(cwd: String): String? {
  val gitDir = resolveGitDir(cwd) ?: return null
  val url = parseGitConfigValue(gitDir, "remote", "origin", "url")
  if (url != null) return url
  val commonDir = getCommonDir(gitDir)
  if (commonDir != null && commonDir != gitDir) {
    return parseGitConfigValue(commonDir, "remote", "origin", "url")
  }
  return null
}

suspend fun isShallowClone(): Boolean {
  val gitDir = resolveGitDir() ?: return false
  val commonDir = getCommonDir(gitDir) ?: gitDir
  val shallow = Path.of(commonDir, "shallow")
  return Files.exists(shallow)
}

suspend fun getWorktreeCountFromFs(): Int {
  return try {
    val gitDir = resolveGitDir() ?: return 0
    val commonDir = getCommonDir(gitDir) ?: gitDir
    val worktrees = Path.of(commonDir, "worktrees")
    if (Files.exists(worktrees)) {
      Files.list(worktrees).use { it.count().toInt() + 1 }
    } else {
      1
    }
  } catch (_: Exception) {
    0
  }
}

suspend fun getHeadForDirSimple(cwd: String): String? = getHeadForDir(cwd)
