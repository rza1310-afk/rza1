package com.example.model

import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId

enum class EdexSectionTab {
  TERMINAL,
  TELEMETRY,
  NETWORK,
  FILES,
}

data class EdexUserPreferences(
  val themeId: EdexThemeId = EdexThemeId.TRON,
  val telemetryThemeId: TelemetryThemeId = TelemetryThemeId.SYNC_HUD,
  val soundEnabled: Boolean = true,
  val scanlinesEnabled: Boolean = true,
  val showKeyboard: Boolean = false,
  val backgroundMonitorEnabled: Boolean = true,
  val glitchShaderEnabled: Boolean = true,
  val glitchLoadThreshold: Float = 75f,
)

data class ProcessEntry(
  val id: Long,
  val name: String,
  val state: String,
  val priority: Int,
  val detail: String,
)

data class NetworkConnectionNode(
  val id: String,
  val label: String,
  val endpoint: String,
  val protocol: String,
  val state: String,
  val latDeg: Double,
  val lonDeg: Double,
  val isOrigin: Boolean = false,
  val trafficWeight: Float = 0.5f,
)

data class SystemTelemetry(
  val timeFormatted: String = "00:00:00",
  val dateFormatted: String = "INIT",
  val timezone: String = "UTC",
  val uptimeFormatted: String = "00:00:00",
  val deviceModel: String = "",
  val osVersion: String = "",
  val kernelVersion: String = "",
  val cpuAbi: String = "",
  val cpuHardware: String = "",
  val cpuCores: Int = 1,
  val cpuLoadPercent: Float = 0f,
  val cpuHistory: List<Float> = List(36) { 0f },
  val coreLoads: List<Float> = emptyList(),
  val cpuFreqMhz: List<Int> = emptyList(),
  val ramUsedMb: Long = 0L,
  val ramTotalMb: Long = 1L,
  val ramHistory: List<Float> = List(36) { 0f },
  val swapUsedMb: Long = 0L,
  val swapTotalMb: Long = 0L,
  val jvmHeapUsedMb: Long = 0L,
  val jvmHeapMaxMb: Long = 1L,
  val storageUsedGb: Float = 0f,
  val storageTotalGb: Float = 1f,
  val storageFreeGb: Float = 0f,
  val batteryPercent: Int = 100,
  val batteryCharging: Boolean = false,
  val batteryTempC: Float = 0f,
  val batteryVoltageMv: Int = 0,
  val batteryStatusLabel: String = "AC / READY",
  val networkConnected: Boolean = false,
  val networkTransport: String = "OFFLINE",
  val interfaceName: String = "lo",
  val ipAddress: String = "127.0.0.1",
  val gatewayIp: String = "--",
  val dnsServers: List<String> = emptyList(),
  val linkDownKbps: Int = 0,
  val linkUpKbps: Int = 0,
  val rxSpeedKbps: Float = 0f,
  val txSpeedKbps: Float = 0f,
  val rxHistory: List<Float> = List(36) { 0f },
  val txHistory: List<Float> = List(36) { 0f },
  val totalRxMb: Float = 0f,
  val totalTxMb: Float = 0f,
  val packetsPerSec: Long = 0L,
  val packetTrafficActive: Boolean = false,
  val packetIntensity: Float = 0f,
  val activeConnections: List<NetworkConnectionNode> = emptyList(),
  val activeProcesses: List<ProcessEntry> = emptyList(),
)

enum class TerminalLineType {
  COMMAND,
  OUTPUT,
  ACCENT,
  SUCCESS,
  ERROR,
  SYSTEM_LOG,
}

data class TerminalLine(
  val id: Long,
  val text: String,
  val type: TerminalLineType = TerminalLineType.OUTPUT,
  val timestamp: String? = null,
)

data class SystemLogEntry(
  val id: Long,
  val timestamp: String,
  val tag: String,
  val message: String,
  val isError: Boolean = false,
)

data class TerminalTab(
  val id: String,
  val title: String,
  val cwd: String,
  val lines: List<TerminalLine>,
  val commandHistory: List<String> = emptyList(),
)

data class FileItem(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val lastModifiedMs: Long,
  val permissions: String,
  val isReadable: Boolean,
)
