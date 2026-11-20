package com.example.data

import com.example.model.SystemLogEntry
import com.example.model.SystemTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TelemetryHub {
  private val logIdGen = AtomicLong(1L)
  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

  private val _telemetry = MutableStateFlow(SystemTelemetry())
  val telemetry: StateFlow<SystemTelemetry> = _telemetry.asStateFlow()

  private val _isServiceRunning = MutableStateFlow(false)
  val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

  private val _sampleCount = MutableStateFlow(0L)
  val sampleCount: StateFlow<Long> = _sampleCount.asStateFlow()

  private val _systemLogs = MutableStateFlow<List<SystemLogEntry>>(emptyList())
  val systemLogs: StateFlow<List<SystemLogEntry>> = _systemLogs.asStateFlow()

  private val _liveLogEvents =
    MutableSharedFlow<SystemLogEntry>(
      extraBufferCapacity = 32,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val liveLogEvents: SharedFlow<SystemLogEntry> = _liveLogEvents.asSharedFlow()

  fun setServiceRunning(running: Boolean) {
    _isServiceRunning.value = running
  }

  fun publish(snapshot: SystemTelemetry) {
    _telemetry.value = snapshot
    _sampleCount.update { it + 1L }
  }

  fun log(tag: String, message: String, isError: Boolean = false) {
    val formattedTime = synchronized(timeFormat) { timeFormat.format(Date()) }
    val entry =
      SystemLogEntry(
        id = logIdGen.incrementAndGet(),
        timestamp = formattedTime,
        tag = tag,
        message = message,
        isError = isError,
      )
    _systemLogs.update { current -> (current + entry).takeLast(200) }
    _liveLogEvents.tryEmit(entry)
  }
}
