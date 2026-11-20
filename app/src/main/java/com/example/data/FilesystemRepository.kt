package com.example.data

import android.content.Context
import com.example.model.FileItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilesystemRepository(private val context: Context) {

  val workspaceRoot: File
    get() = context.filesDir

  fun ensureWorkspaceSeeded() {
    val readme = File(workspaceRoot, "README.edex")
    if (!readme.exists()) {
      readme.writeText(
        buildString {
          appendLine("eDEX-UI // ANDROID TERMINAL WORKSPACE")
          appendLine("=====================================")
          appendLine("Workspace path: ${workspaceRoot.absolutePath}")
          appendLine()
          appendLine("Available built-in terminal commands:")
          appendLine("  help, neofetch, sysinfo, ls, cd, pwd, cat, touch, mkdir, rm")
          appendLine("  ifconfig, ping <host>, df, free, ps, theme <name>, clear")
          appendLine()
          appendLine("Standard Android shell binaries (/system/bin/sh) also execute directly.")
        }
      )
    }
    val scriptsDir = File(workspaceRoot, "scripts")
    if (!scriptsDir.exists()) {
      scriptsDir.mkdirs()
      File(scriptsDir, "sys_check.sh")
        .writeText("#!/system/bin/sh\nuname -a\nid\ndf -h /data\n")
    }
  }

  suspend fun listDirectory(path: String): List<FileItem> =
    withContext(Dispatchers.IO) {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
      val children = dir.listFiles()?.toList() ?: emptyList()
      children
        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .map { file ->
          val perms = buildString {
            append(if (file.isDirectory) 'd' else '-')
            append(if (file.canRead()) 'r' else '-')
            append(if (file.canWrite()) 'w' else '-')
            append(if (file.canExecute()) 'x' else '-')
          }
          FileItem(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isDirectory) (file.list()?.size?.toLong() ?: 0L) else file.length(),
            lastModifiedMs = file.lastModified(),
            permissions = perms,
            isReadable = file.canRead(),
          )
        }
    }

  suspend fun readFileContent(path: String, maxBytes: Int = 64 * 1024): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "Target is not a regular file." }
        require(file.canRead()) { "Permission denied reading ${file.name}." }
        val bytes = file.inputStream().use { input ->
          val buf = ByteArray(maxBytes)
          val read = input.read(buf)
          if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
        val text = bytes.decodeToString()
        if (file.length() > maxBytes) {
          "$text\n\n... [TRUNCATED AT ${maxBytes / 1024} KB]"
        } else {
          text
        }
      }
    }

  fun isWithinWorkspace(path: String, allowRoot: Boolean = true): Boolean {
    return runCatching {
      val root = workspaceRoot.canonicalPath
      val target = File(path).canonicalPath
      if (target == root) allowRoot else target.startsWith(root + File.separator)
    }.getOrDefault(false)
  }

  suspend fun createFile(parentPath: String, name: String, content: String = ""): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitized = name.trim().replace("/", "").replace("\\", "")
        require(sanitized.isNotEmpty() && sanitized != "." && sanitized != "..") {
          "Invalid filename."
        }
        val target = File(parentPath, sanitized)
        check(isWithinWorkspace(target.canonicalPath, allowRoot = false)) {
          "Protected system path: file creation is restricted to app workspace."
        }
        target.writeText(content)
        target
      }
    }

  suspend fun createDirectory(parentPath: String, name: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitized = name.trim().replace("/", "").replace("\\", "")
        require(sanitized.isNotEmpty() && sanitized != "." && sanitized != "..") {
          "Invalid directory name."
        }
        val target = File(parentPath, sanitized)
        check(isWithinWorkspace(target.canonicalPath, allowRoot = false)) {
          "Protected system path: directory creation is restricted to app workspace."
        }
        check(target.mkdirs() || target.isDirectory) { "Unable to create directory." }
        target
      }
    }

  suspend fun writeFile(path: String, content: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        check(isWithinWorkspace(path, allowRoot = false)) {
          "Protected system path: writes are restricted to app workspace."
        }
        File(path).writeText(content)
      }
    }

  suspend fun deleteItem(path: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        check(isWithinWorkspace(path, allowRoot = false)) {
          "Protected system path: deletions are restricted to app workspace."
        }
        val target = File(path)
        check(target.deleteRecursively()) { "Failed to delete ${target.name}" }
      }
    }
}
