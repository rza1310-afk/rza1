package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SystemTelemetryRepository
import com.example.data.TelemetryHub
import com.example.model.SystemTelemetry
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetryMonitorService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val stateLock = Any()
  private lateinit var telemetryRepo: SystemTelemetryRepository
  private var pollingJob: Job? = null
  private var refreshJob: Job? = null
  private var batteryReceiverRegistered = false
  private var networkCallbackRegistered = false
  private var lastNotificationUpdateMs = 0L
  private var lastRefreshTriggerMs = 0L
  private var lastBatteryPct = -1
  private var lastCharging: Boolean? = null
  private var lastNetworkState: String? = null
  private var tickCounter = 0L

  private val batteryReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        triggerImmediateRefresh()
      }
    }

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        triggerImmediateRefresh()
      }

      override fun onLost(network: Network) {
        triggerImmediateRefresh()
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        triggerImmediateRefresh()
      }
    }

  override fun onCreate() {
    super.onCreate()
    telemetryRepo = SystemTelemetryRepository(applicationContext)
    createNotificationChannel()

    val initial = telemetryRepo.collectSnapshot()
    TelemetryHub.setServiceRunning(true)
    TelemetryHub.log(
      "DAEMON",
      "TelemetryMonitorService online (type=dataSync, interval=1000ms)",
    )

    val notification = buildNotification(initial)
    val startedForeground =
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
          )
        } else {
          startForeground(NOTIFICATION_ID, notification)
        }
      }
    if (startedForeground.isFailure) {
      val err = startedForeground.exceptionOrNull()
      TelemetryHub.log(
        "DAEMON",
        "Foreground start rejected: ${err?.message ?: err?.javaClass?.simpleName}",
        isError = true,
      )
      TelemetryHub.setServiceRunning(false)
      stopSelf()
      return
    }

    synchronized(stateLock) { lastNotificationUpdateMs = System.currentTimeMillis() }
    publishAndLog(initial, updateNotification = false)
    registerSystemObservers()
    startTelemetryLoop(initialDelay = true)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      TelemetryHub.log("DAEMON", "Stop action received from notification.")
      stopSelf()
      return START_NOT_STICKY
    }
    if (pollingJob == null || pollingJob?.isActive != true) {
      startTelemetryLoop(initialDelay = false)
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    pollingJob?.cancel()
    refreshJob?.cancel()
    unregisterSystemObservers()
    TelemetryHub.log("DAEMON", "TelemetryMonitorService offline.")
    TelemetryHub.setServiceRunning(false)
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startTelemetryLoop(initialDelay: Boolean = false) {
    pollingJob?.cancel()
    pollingJob =
      serviceScope.launch {
        if (initialDelay) {
          delay(1000L)
        }
        while (isActive) {
          val snapshot = telemetryRepo.collectSnapshot()
          publishAndLog(snapshot)
          delay(1000L)
        }
      }
  }

  private fun triggerImmediateRefresh() {
    val now = System.currentTimeMillis()
    synchronized(stateLock) {
      if (now - lastRefreshTriggerMs < 300L) return
      lastRefreshTriggerMs = now
    }
    refreshJob?.cancel()
    refreshJob =
      serviceScope.launch {
        val snapshot = telemetryRepo.collectSnapshot()
        publishAndLog(snapshot)
      }
  }

  private fun publishAndLog(snapshot: SystemTelemetry, updateNotification: Boolean = true) {
    TelemetryHub.publish(snapshot)
    if (updateNotification) {
      maybeUpdateNotification(snapshot)
    }

    synchronized(stateLock) {
      if (lastBatteryPct != snapshot.batteryPercent || lastCharging != snapshot.batteryCharging) {
        lastBatteryPct = snapshot.batteryPercent
        lastCharging = snapshot.batteryCharging
        TelemetryHub.log(
          "BAT",
          "${snapshot.batteryPercent}% (${snapshot.batteryStatusLabel}) • ${String.format(Locale.US, "%.1f°C", snapshot.batteryTempC)}",
        )
      }

      val netSig = "${snapshot.networkConnected}:${snapshot.networkTransport}:${snapshot.ipAddress}"
      if (lastNetworkState != netSig) {
        lastNetworkState = netSig
        TelemetryHub.log(
          "NET",
          "${snapshot.networkTransport} [${snapshot.interfaceName} ${snapshot.ipAddress}]",
          isError = !snapshot.networkConnected,
        )
      }

      tickCounter++
      if (tickCounter % 15L == 0L) {
        TelemetryHub.log(
          "SYS",
          String.format(
            Locale.US,
            "CPU %d%% • RAM %d/%dMB • ↓%.1f ↑%.1f KB/s • PKT %d/s (%d NODES)",
            snapshot.cpuLoadPercent.toInt(),
            snapshot.ramUsedMb,
            snapshot.ramTotalMb,
            snapshot.rxSpeedKbps,
            snapshot.txSpeedKbps,
            snapshot.packetsPerSec,
            snapshot.activeConnections.size,
          ),
        )
      }
    }
  }

  private fun registerSystemObservers() {
    runCatching {
      ContextCompat.registerReceiver(
        this,
        batteryReceiver,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      batteryReceiverRegistered = true
    }

    runCatching {
      val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      cm?.registerDefaultNetworkCallback(networkCallback)
      networkCallbackRegistered = cm != null
    }
  }

  private fun unregisterSystemObservers() {
    if (batteryReceiverRegistered) {
      runCatching { unregisterReceiver(batteryReceiver) }
      batteryReceiverRegistered = false
    }
    if (networkCallbackRegistered) {
      runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.unregisterNetworkCallback(networkCallback)
      }
      networkCallbackRegistered = false
    }
  }

  private fun maybeUpdateNotification(snapshot: SystemTelemetry) {
    val shouldNotify =
      synchronized(stateLock) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs < 2000L) {
          false
        } else {
          lastNotificationUpdateMs = now
          true
        }
      }
    if (!shouldNotify) return

    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    nm.notify(NOTIFICATION_ID, buildNotification(snapshot))
  }

  private fun buildNotification(telemetry: SystemTelemetry): Notification {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent = Intent(this, TelemetryMonitorService::class.java).apply { action = ACTION_STOP }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val summary =
      String.format(
        Locale.US,
        "CPU %d%% • BAT %d%% (%s) • ↓%.1f ↑%.1f KB/s",
        telemetry.cpuLoadPercent.toInt(),
        telemetry.batteryPercent,
        if (telemetry.batteryCharging) "CHG" else "BAT",
        telemetry.rxSpeedKbps,
        telemetry.txSpeedKbps,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_telemetry)
      .setContentTitle(getString(R.string.fgs_notification_title))
      .setContentText(summary)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(openPendingIntent)
      .addAction(0, getString(R.string.fgs_action_stop), stopPendingIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = getString(R.string.fgs_channel_description)
            setShowBadge(false)
          }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "edex_telemetry_daemon"
    const val NOTIFICATION_ID = 4096
    const val ACTION_STOP = "com.example.action.STOP_TELEMETRY_SERVICE"

    fun start(context: Context) {
      val intent = Intent(context, TelemetryMonitorService::class.java)
      runCatching { ContextCompat.startForegroundService(context, intent) }
        .onFailure { err ->
          TelemetryHub.log(
            "DAEMON",
            "Unable to start telemetry service: ${err.message ?: err::class.java.simpleName}",
            isError = true,
          )
        }
    }

    fun stop(context: Context) {
      val intent = Intent(context, TelemetryMonitorService::class.java)
      context.stopService(intent)
    }
  }
}
