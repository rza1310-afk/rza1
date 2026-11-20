package com.example.data

import android.os.Build
import com.example.model.SystemTelemetry
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class TerminalExecResult(
  val newCwd: String,
  val outputLines: List<Pair<String, TerminalLineType>>,
  val clearScreen: Boolean = false,
  val themeChange: EdexThemeId? = null,
  val telemetryThemeChange: TelemetryThemeId? = null,
  val soundChange: Boolean? = null,
  val scanlinesChange: Boolean? = null,
  val daemonChange: Boolean? = null,
  val glitchChange: Boolean? = null,
  val glitchThresholdChange: Float? = null,
  val replayBoot: Boolean = false,
)

class TerminalEngine(private val filesystemRepository: FilesystemRepository) {

  suspend fun execute(
    rawCommand: String,
    cwd: String,
    telemetry: SystemTelemetry,
  ): TerminalExecResult =
    withContext(Dispatchers.IO) {
      val trimmed = rawCommand.trim()
      if (trimmed.isEmpty()) {
        return@withContext TerminalExecResult(cwd, emptyList())
      }

      val tokens = parseArgs(trimmed)
      val cmd = tokens.firstOrNull()?.lowercase(Locale.US) ?: ""
      val args = tokens.drop(1)

      when (cmd) {
        "clear", "cls" ->
          TerminalExecResult(cwd, emptyList(), clearScreen = true)

        "help", "?" ->
          TerminalExecResult(
            cwd,
            listOf(
              "eDEX-UI KERNEL COMMAND MATRIX" to TerminalLineType.ACCENT,
              "  help                Show this command reference" to TerminalLineType.OUTPUT,
              "  neofetch | sysinfo  Display full hardware & OS HUD summary" to TerminalLineType.OUTPUT,
              "  theme <id>          Switch palette: tron, blade, apollo, matrix, chalk" to TerminalLineType.OUTPUT,
              "  hudtheme <id>       Telemetry palette: sync, cyberpunk, monochrome, military" to TerminalLineType.OUTPUT,
              "  sound <on|off>      Toggle synthesized HUD audio" to TerminalLineType.OUTPUT,
              "  scanlines <on|off>  Toggle CRT scanline filter" to TerminalLineType.OUTPUT,
              "  daemon <start|stop> Control Foreground Telemetry Service" to TerminalLineType.OUTPUT,
              "  glitch <on|off|N>   Configure overload glitch shader & threshold" to TerminalLineType.OUTPUT,
              "  logs | logcat       Dump system & telemetry log buffer" to TerminalLineType.OUTPUT,
              "  reboot              Replay eDEX-UI boot diagnostics" to TerminalLineType.OUTPUT,
              "  pwd / cd / ls / ll  Navigate & inspect filesystem" to TerminalLineType.OUTPUT,
              "  cat / touch / mkdir Inspect or create files in workspace" to TerminalLineType.OUTPUT,
              "  ifconfig | ip       Dump active network interfaces & routes" to TerminalLineType.OUTPUT,
              "  ping <host>         Probe TCP/ICMP latency to target host" to TerminalLineType.OUTPUT,
              "  free / df / ps      Memory, storage partitions & active threads" to TerminalLineType.OUTPUT,
              "  <any sh cmd>        Executed natively via /system/bin/sh" to TerminalLineType.SUCCESS,
            ),
          )

        "neofetch", "sysinfo" ->
          TerminalExecResult(
            cwd,
            listOf(
              "   ______  ____   ______ _  __      __  ______" to TerminalLineType.ACCENT,
              "  / ____/ / __ \\ / ____/| |/ /     / / / /  _/" to TerminalLineType.ACCENT,
              " / __/   / / / // __/   |   /_____/ / / // /  " to TerminalLineType.ACCENT,
              "/ /___  / /_/ // /___  /   /_____/ /_/ // /   " to TerminalLineType.ACCENT,
              "\\____/ /_____//_____/ /_/|_|     \\____/___/   " to TerminalLineType.ACCENT,
              "----------------------------------------------" to TerminalLineType.OUTPUT,
              "  HOST      : ${telemetry.deviceModel}" to TerminalLineType.OUTPUT,
              "  OS        : ${telemetry.osVersion}" to TerminalLineType.OUTPUT,
              "  KERNEL    : ${telemetry.kernelVersion} (${telemetry.cpuAbi})" to TerminalLineType.OUTPUT,
              "  UPTIME    : ${telemetry.uptimeFormatted}" to TerminalLineType.OUTPUT,
              "  CPU       : ${telemetry.cpuHardware} (${telemetry.cpuCores} CORES @ ${telemetry.cpuLoadPercent.toInt()}%)" to TerminalLineType.OUTPUT,
              "  MEMORY    : ${telemetry.ramUsedMb} MB / ${telemetry.ramTotalMb} MB" to TerminalLineType.OUTPUT,
              "  STORAGE   : ${String.format(Locale.US, "%.1f", telemetry.storageUsedGb)} GB / ${String.format(Locale.US, "%.1f", telemetry.storageTotalGb)} GB" to TerminalLineType.OUTPUT,
              "  BATTERY   : ${telemetry.batteryPercent}% (${telemetry.batteryStatusLabel}) ${telemetry.batteryTempC}°C" to TerminalLineType.OUTPUT,
              "  NETWORK   : ${telemetry.networkTransport} [${telemetry.interfaceName} ${telemetry.ipAddress}]" to TerminalLineType.SUCCESS,
            ),
          )

        "theme" -> {
          val target = args.firstOrNull()?.uppercase(Locale.US)
          val match = EdexThemeId.entries.firstOrNull { it.name == target }
          if (match != null) {
            TerminalExecResult(
              cwd,
              listOf("Theme matrix switched to [${match.displayName}] — ${match.subtitle}" to TerminalLineType.SUCCESS),
              themeChange = match,
            )
          } else {
            TerminalExecResult(
              cwd,
              listOf("Usage: theme <tron|blade|apollo|matrix|chalk>" to TerminalLineType.ERROR),
            )
          }
        }

        "hudtheme", "telemetrytheme" -> {
          val target = args.firstOrNull()?.lowercase(Locale.US)
          val match =
            when (target) {
              "sync", "hud", "default", "sync_hud", "sync-hud" -> TelemetryThemeId.SYNC_HUD
              "cyberpunk", "neon", "cyberpunk_neon", "cyberpunk-neon" ->
                TelemetryThemeId.CYBERPUNK_NEON
              "mono", "monochrome", "terminal", "monochrome_terminal", "monochrome-terminal" ->
                TelemetryThemeId.MONOCHROME_TERMINAL
              "military", "tactical", "nvg", "military_hud", "military-hud" ->
                TelemetryThemeId.MILITARY_HUD
              else -> null
            }
          if (match != null) {
            TerminalExecResult(
              cwd,
              listOf(
                "Telemetry HUD palette switched to [${match.displayName}] — ${match.subtitle}" to
                  TerminalLineType.SUCCESS
              ),
              telemetryThemeChange = match,
            )
          } else {
            TerminalExecResult(
              cwd,
              listOf(
                "Usage: hudtheme <sync|cyberpunk|monochrome|military>" to TerminalLineType.ERROR
              ),
            )
          }
        }

        "sound" -> {
          when (args.firstOrNull()?.lowercase(Locale.US)) {
            "on", "true", "1" ->
              TerminalExecResult(cwd, listOf("Audio synthesizer ENABLED." to TerminalLineType.SUCCESS), soundChange = true)
            "off", "false", "0" ->
              TerminalExecResult(cwd, listOf("Audio synthesizer MUTED." to TerminalLineType.ACCENT), soundChange = false)
            else ->
              TerminalExecResult(cwd, listOf("Usage: sound <on|off>" to TerminalLineType.ERROR))
          }
        }

        "scanlines" -> {
          when (args.firstOrNull()?.lowercase(Locale.US)) {
            "on", "true", "1" ->
              TerminalExecResult(cwd, listOf("CRT scanline overlay ENABLED." to TerminalLineType.SUCCESS), scanlinesChange = true)
            "off", "false", "0" ->
              TerminalExecResult(cwd, listOf("CRT scanline overlay DISABLED." to TerminalLineType.ACCENT), scanlinesChange = false)
            else ->
              TerminalExecResult(cwd, listOf("Usage: scanlines <on|off>" to TerminalLineType.ERROR))
          }
        }

        "daemon" -> {
          when (args.firstOrNull()?.lowercase(Locale.US)) {
            "start", "on" ->
              TerminalExecResult(
                cwd,
                listOf("Starting TelemetryMonitorService (Foreground Service)..." to TerminalLineType.SUCCESS),
                daemonChange = true,
              )
            "stop", "off" ->
              TerminalExecResult(
                cwd,
                listOf("Stopping TelemetryMonitorService..." to TerminalLineType.ACCENT),
                daemonChange = false,
              )
            "status" -> {
              val stateLabel = if (TelemetryHub.isServiceRunning.value) "ACTIVE" else "STOPPED"
              TerminalExecResult(
                cwd,
                listOf(
                  "DAEMON: $stateLabel | SAMPLES: ${TelemetryHub.sampleCount.value}" to
                    TerminalLineType.ACCENT
                ),
              )
            }
            else ->
              TerminalExecResult(cwd, listOf("Usage: daemon <start|stop|status>" to TerminalLineType.ERROR))
          }
        }

        "logs", "dmesg" -> {
          val entries = TelemetryHub.systemLogs.value.takeLast(40)
          if (entries.isEmpty()) {
            TerminalExecResult(cwd, listOf("(no system logs recorded yet)" to TerminalLineType.OUTPUT))
          } else {
            val lines =
              entries.map { entry ->
                val text = "[${entry.timestamp}] [${entry.tag}] ${entry.message}"
                val type =
                  if (entry.isError) TerminalLineType.ERROR else TerminalLineType.SYSTEM_LOG
                text to type
              }
            TerminalExecResult(cwd, lines)
          }
        }

        "logcat" -> {
          val sysLines = runSystemShell("logcat -d -t 30 -v time", cwd).outputLines
          val daemonHeader = listOf("--- SYSTEM LOG BUFFER (logcat) ---" to TerminalLineType.ACCENT)
          TerminalExecResult(cwd, daemonHeader + sysLines)
        }

        "glitch" -> {
          val sub = args.firstOrNull()?.lowercase(Locale.US)
          val numeric = sub?.toFloatOrNull()
          when {
            sub == "on" || sub == "true" ->
              TerminalExecResult(
                cwd,
                listOf("Overload Glitch Shader ENABLED." to TerminalLineType.SUCCESS),
                glitchChange = true,
              )
            sub == "off" || sub == "false" ->
              TerminalExecResult(
                cwd,
                listOf("Overload Glitch Shader DISABLED." to TerminalLineType.ACCENT),
                glitchChange = false,
              )
            numeric != null -> {
              val clamped = numeric.coerceIn(20f, 95f)
              TerminalExecResult(
                cwd,
                listOf("Glitch load threshold set to ${clamped.toInt()}%." to TerminalLineType.SUCCESS),
                glitchThresholdChange = clamped,
              )
            }
            else ->
              TerminalExecResult(
                cwd,
                listOf("Usage: glitch <on|off|20..95>" to TerminalLineType.ERROR),
              )
          }
        }

        "reboot" ->
          TerminalExecResult(
            cwd,
            listOf("Re-initializing eDEX-UI subsystem..." to TerminalLineType.ACCENT),
            replayBoot = true,
          )

        "pwd" ->
          TerminalExecResult(cwd, listOf(cwd to TerminalLineType.OUTPUT))

        "cd" -> {
          val targetArg = args.firstOrNull() ?: filesystemRepository.workspaceRoot.absolutePath
          val resolved = resolvePath(cwd, targetArg)
          if (resolved.exists() && resolved.isDirectory) {
            TerminalExecResult(
              resolved.canonicalPath,
              listOf("CWD -> ${resolved.canonicalPath}" to TerminalLineType.ACCENT),
            )
          } else {
            TerminalExecResult(
              cwd,
              listOf("cd: $targetArg: No such directory" to TerminalLineType.ERROR),
            )
          }
        }

        "ls", "ll", "dir" -> {
          val targetArg = args.firstOrNull { !it.startsWith("-") }
          val targetDir = if (targetArg == null) File(cwd) else resolvePath(cwd, targetArg)
          if (!targetDir.exists()) {
            TerminalExecResult(cwd, listOf("ls: ${targetDir.path}: No such file or directory" to TerminalLineType.ERROR))
          } else if (targetDir.isFile) {
            TerminalExecResult(cwd, listOf("${targetDir.name} (${targetDir.length()} B)" to TerminalLineType.OUTPUT))
          } else {
            val entries = filesystemRepository.listDirectory(targetDir.absolutePath)
            if (entries.isEmpty()) {
              TerminalExecResult(cwd, listOf("(empty directory)" to TerminalLineType.OUTPUT))
            } else {
              val lines =
                entries.map { item ->
                  val sizeStr =
                    if (item.isDirectory) "<DIR>" else formatBytes(item.sizeBytes)
                  val lineText =
                    String.format(Locale.US, "%-5s  %9s  %s", item.permissions, sizeStr, item.name)
                  val type =
                    if (item.isDirectory) TerminalLineType.ACCENT else TerminalLineType.OUTPUT
                  lineText to type
                }
              TerminalExecResult(cwd, lines)
            }
          }
        }

        "cat" -> {
          val fileArg = args.firstOrNull()
          if (fileArg == null) {
            TerminalExecResult(cwd, listOf("Usage: cat <filename>" to TerminalLineType.ERROR))
          } else {
            val file = resolvePath(cwd, fileArg)
            val result = filesystemRepository.readFileContent(file.absolutePath)
            result.fold(
              onSuccess = { content ->
                TerminalExecResult(
                  cwd,
                  content.lines().map { it to TerminalLineType.OUTPUT },
                )
              },
              onFailure = { err ->
                TerminalExecResult(cwd, listOf("cat: ${err.message}" to TerminalLineType.ERROR))
              },
            )
          }
        }

        "touch" -> {
          val fileArg = args.firstOrNull()
          if (fileArg == null) {
            TerminalExecResult(cwd, listOf("Usage: touch <filename>" to TerminalLineType.ERROR))
          } else {
            val res = filesystemRepository.createFile(cwd, fileArg)
            res.fold(
              onSuccess = {
                TerminalExecResult(cwd, listOf("Created ${it.name}" to TerminalLineType.SUCCESS))
              },
              onFailure = {
                TerminalExecResult(cwd, listOf("touch: ${it.message}" to TerminalLineType.ERROR))
              },
            )
          }
        }

        "mkdir" -> {
          val dirArg = args.firstOrNull()
          if (dirArg == null) {
            TerminalExecResult(cwd, listOf("Usage: mkdir <dirname>" to TerminalLineType.ERROR))
          } else {
            val res = filesystemRepository.createDirectory(cwd, dirArg)
            res.fold(
              onSuccess = {
                TerminalExecResult(cwd, listOf("Directory created: ${it.name}" to TerminalLineType.SUCCESS))
              },
              onFailure = {
                TerminalExecResult(cwd, listOf("mkdir: ${it.message}" to TerminalLineType.ERROR))
              },
            )
          }
        }

        "rm" -> {
          val targetArg = args.firstOrNull { !it.startsWith("-") }
          if (targetArg == null) {
            TerminalExecResult(cwd, listOf("Usage: rm <name>" to TerminalLineType.ERROR))
          } else {
            val target = resolvePath(cwd, targetArg)
            val res = filesystemRepository.deleteItem(target.absolutePath)
            res.fold(
              onSuccess = {
                TerminalExecResult(cwd, listOf("Removed ${target.name}" to TerminalLineType.SUCCESS))
              },
              onFailure = {
                TerminalExecResult(cwd, listOf("rm: ${it.message}" to TerminalLineType.ERROR))
              },
            )
          }
        }

        "free" ->
          TerminalExecResult(
            cwd,
            listOf(
              "              TOTAL        USED        FREE" to TerminalLineType.ACCENT,
              String.format(
                Locale.US,
                "MEM (MB)   %8d    %8d    %8d",
                telemetry.ramTotalMb,
                telemetry.ramUsedMb,
                (telemetry.ramTotalMb - telemetry.ramUsedMb).coerceAtLeast(0L),
              ) to TerminalLineType.OUTPUT,
              String.format(
                Locale.US,
                "SWAP (MB)  %8d    %8d    %8d",
                telemetry.swapTotalMb,
                telemetry.swapUsedMb,
                (telemetry.swapTotalMb - telemetry.swapUsedMb).coerceAtLeast(0L),
              ) to TerminalLineType.OUTPUT,
              String.format(
                Locale.US,
                "JVM HEAP   %8d    %8d    %8d",
                telemetry.jvmHeapMaxMb,
                telemetry.jvmHeapUsedMb,
                (telemetry.jvmHeapMaxMb - telemetry.jvmHeapUsedMb).coerceAtLeast(0L),
              ) to TerminalLineType.OUTPUT,
            ),
          )

        "df" ->
          TerminalExecResult(
            cwd,
            listOf(
              "FILESYSTEM          SIZE       USED      AVAIL   MOUNT" to TerminalLineType.ACCENT,
              String.format(
                Locale.US,
                "/dev/block/data   %5.1fG     %5.1fG     %5.1fG   /data",
                telemetry.storageTotalGb,
                telemetry.storageUsedGb,
                telemetry.storageFreeGb,
              ) to TerminalLineType.OUTPUT,
            ),
          )

        "ps" -> {
          val header = "PID/TID      STATE  PRIO  NAME" to TerminalLineType.ACCENT
          val rows =
            telemetry.activeProcesses.map { proc ->
              String.format(
                Locale.US,
                "%-11d  %-5s  %4d  %s",
                proc.id,
                proc.state,
                proc.priority,
                proc.name,
              ) to TerminalLineType.OUTPUT
            }
          TerminalExecResult(cwd, listOf(header) + rows)
        }

        "ifconfig", "ip" -> {
          val lines = mutableListOf<Pair<String, TerminalLineType>>()
          lines += "ACTIVE LINK: ${telemetry.networkTransport}" to TerminalLineType.ACCENT
          lines += "  IFACE    : ${telemetry.interfaceName}" to TerminalLineType.OUTPUT
          lines += "  IPV4     : ${telemetry.ipAddress}" to TerminalLineType.OUTPUT
          lines += "  GATEWAY  : ${telemetry.gatewayIp}" to TerminalLineType.OUTPUT
          if (telemetry.dnsServers.isNotEmpty()) {
            lines += "  DNS      : ${telemetry.dnsServers.joinToString(", ")}" to TerminalLineType.OUTPUT
          }
          runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp }?.forEach { ni ->
              val addrs = ni.inetAddresses.toList().mapNotNull { it.hostAddress }.joinToString(", ")
              if (addrs.isNotBlank()) {
                lines += "  ${ni.name.padEnd(8)} : $addrs" to TerminalLineType.OUTPUT
              }
            }
          }
          TerminalExecResult(cwd, lines)
        }

        "ping" -> {
          val host = args.firstOrNull { !it.startsWith("-") } ?: "1.1.1.1"
          val pingLines = probeHost(host)
          TerminalExecResult(cwd, pingLines)
        }

        "date" ->
          TerminalExecResult(
            cwd,
            listOf(
              SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).format(Date()) to
                TerminalLineType.OUTPUT
            ),
          )

        "echo" ->
          TerminalExecResult(cwd, listOf(args.joinToString(" ") to TerminalLineType.OUTPUT))

        else -> runSystemShell(trimmed, cwd)
      }
    }

  private fun probeHost(host: String): List<Pair<String, TerminalLineType>> {
    val results = mutableListOf<Pair<String, TerminalLineType>>()
    return runCatching {
      val addr = InetAddress.getByName(host)
      results += "PING $host (${addr.hostAddress}) — 3 probes" to TerminalLineType.ACCENT
      var successes = 0
      val latencies = mutableListOf<Long>()
      for (seq in 1..3) {
        val startNs = System.nanoTime()
        var reached = false
        // Try ICMP reachability first, then TCP port 443/80 fallback (works on non-root Android)
        reached = runCatching { addr.isReachable(1500) }.getOrDefault(false)
        if (!reached) {
          for (port in listOf(443, 80, 53)) {
            val ok =
              runCatching {
                Socket().use { s ->
                  s.connect(InetSocketAddress(addr, port), 1500)
                }
                true
              }.getOrDefault(false)
            if (ok) {
              reached = true
              break
            }
          }
        }
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        if (reached) {
          successes++
          latencies += elapsedMs
          results += "seq=$seq from ${addr.hostAddress}: time=${elapsedMs} ms" to TerminalLineType.SUCCESS
        } else {
          results += "seq=$seq timeout reaching ${addr.hostAddress}" to TerminalLineType.ERROR
        }
      }
      if (latencies.isNotEmpty()) {
        val avg = latencies.average()
        results +=
          String.format(Locale.US, "--- %d/3 packets acknowledged | avg=%.1f ms ---", successes, avg) to
            TerminalLineType.ACCENT
      }
      results
    }.getOrElse { err ->
      listOf("ping: cannot resolve $host (${err.message})" to TerminalLineType.ERROR)
    }
  }

  private suspend fun runSystemShell(command: String, cwd: String): TerminalExecResult =
    coroutineScope {
      runCatching {
        val shellBin = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
        val pb =
          ProcessBuilder(shellBin, "-c", command).apply {
            val dir = File(cwd)
            if (dir.exists() && dir.isDirectory) directory(dir)
            redirectErrorStream(true)
          }
        val process = pb.start()
        try {
          val outputDeferred =
            async(Dispatchers.IO) {
              process.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(2048)
                while (sb.length < 32_768) {
                  val count = reader.read(buf)
                  if (count < 0) break
                  sb.append(buf, 0, count)
                }
                sb.toString()
              }
            }

          val exitCode =
            withTimeoutOrNull(8_000L) {
              runInterruptible(Dispatchers.IO) { process.waitFor() }
            }

          if (exitCode == null) {
            destroyProcessCompat(process)
            outputDeferred.cancel()
            return@coroutineScope TerminalExecResult(
              cwd,
              listOf("Command timed out after 8s." to TerminalLineType.ERROR),
            )
          }

          val output = withTimeoutOrNull(500L) { outputDeferred.await() } ?: ""
          val lines =
            output
              .trimEnd()
              .lines()
              .filterIndexed { index, s -> !(index == 0 && s.isEmpty()) }
              .take(120)
              .map { line ->
                line to if (exitCode == 0) TerminalLineType.OUTPUT else TerminalLineType.ERROR
              }
          val finalLines =
            if (lines.isEmpty() && exitCode == 0) {
              listOf("[exit 0]" to TerminalLineType.SUCCESS)
            } else if (lines.isEmpty()) {
              listOf("[exit $exitCode]" to TerminalLineType.ERROR)
            } else {
              lines
            }
          TerminalExecResult(cwd, finalLines)
        } finally {
          destroyProcessCompat(process)
        }
      }.getOrElse { err ->
        TerminalExecResult(cwd, listOf("sh: ${err.message}" to TerminalLineType.ERROR))
      }
    }

  private fun destroyProcessCompat(process: Process) {
    val stillAlive = runCatching { process.exitValue() }.isFailure
    if (stillAlive) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        process.destroyForcibly()
      } else {
        process.destroy()
      }
    }
    runCatching { process.inputStream.close() }
    runCatching { process.outputStream.close() }
    runCatching { process.errorStream.close() }
  }

  private fun resolvePath(cwd: String, target: String): File {
    val expanded =
      when {
        target == "~" -> filesystemRepository.workspaceRoot.absolutePath
        target.startsWith("~/") ->
          File(filesystemRepository.workspaceRoot, target.removePrefix("~/")).absolutePath
        else -> target
      }
    val f = File(expanded)
    return if (f.isAbsolute) f else File(cwd, expanded)
  }

  private fun parseArgs(input: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var quoteChar = '"'
    for (ch in input) {
      when {
        inQuotes && ch == quoteChar -> inQuotes = false
        !inQuotes && (ch == '"' || ch == '\'') -> {
          inQuotes = true
          quoteChar = ch
        }
        !inQuotes && ch.isWhitespace() -> {
          if (current.isNotEmpty()) {
            result.add(current.toString())
            current.clear()
          }
        }
        else -> current.append(ch)
      }
    }
    if (current.isNotEmpty()) result.add(current.toString())
    return result
  }

  private fun formatBytes(bytes: Long): String {
    return when {
      bytes < 1024L -> "${bytes} B"
      bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
      else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
  }
}

fun createWelcomeTerminalLines(deviceModel: String): List<TerminalLine> {
  var nextId = 1L
  fun line(text: String, type: TerminalLineType = TerminalLineType.OUTPUT) =
    TerminalLine(id = nextId++, text = text, type = type)

  return listOf(
    line("eDEX-UI TERMINAL SUBSYSTEM // v2.2-ANDROID", TerminalLineType.ACCENT),
    line("HOST: $deviceModel | SHELL: /system/bin/sh", TerminalLineType.SUCCESS),
    line("Type 'help' for commands, 'neofetch' for telemetry, or any shell command.", TerminalLineType.OUTPUT),
  )
}
