package rj.cocacode.utils.git

import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

suspend fun isPathGitignored(filePath: String, cwd: String): Boolean {
  return try {
    val pb = ProcessBuilder("git", "check-ignore", filePath)
    pb.directory(File(cwd))
    val p = pb.start()
    val exitCode = p.waitFor()
    exitCode == 0
  } catch (_: Exception) {
    false
  }
}

fun getGlobalGitignorePath(): String {
  val home = System.getProperty("user.home")
  return Paths.get(home, ".config", "git", "ignore").toString()
}

suspend fun addFileGlobRuleToGitignore(
  filename: String,
  cwd: String? = null,
): Unit {
  try {
    val actualCwd = cwd ?: System.getProperty("user.dir")
    if (!dirIsInGitRepo(actualCwd)) return

    val gitignoreEntry = "**/$filename"
    val testPath = if (filename.endsWith("/")) "$filename/sample-file.txt" else filename
    if (isPathGitignored(testPath, actualCwd)) return

    val globalGitignorePath = getGlobalGitignorePath()
    val configGitDir = java.nio.file.Paths.get(globalGitignorePath).parent
    java.nio.file.Files.createDirectories(configGitDir)

    try {
      val path = Paths.get(globalGitignorePath)
      val content = Files.readString(path)
      if (content.contains(gitignoreEntry)) return
      Files.writeString(path, "\n$gitignoreEntry\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND)
    } catch (e: FileNotFoundException) {
      Files.writeString(Paths.get(globalGitignorePath), "$gitignoreEntry\n", StandardCharsets.UTF_8)
    }
  } catch (e: Exception) {
    }
}

fun dirIsInGitRepo(cwd: String): Boolean {
  var p: Path? = java.nio.file.Paths.get(cwd).toAbsolutePath()
  while (p != null) {
    val gitPath = p.resolve(".git")
    if (java.nio.file.Files.exists(gitPath)) return true
    p = p.parent
  }
  return false
}
