package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

enum class HudSoundEffect {
  TERMINAL_TYPE,
  TERMINAL_BACKSPACE,
  DATA_PROCESS,
  DATA_BURST,
  UI_SWITCH,
  COMMAND_EXEC,
  ERROR_THUD,
  BOOT_HUM,
}

/**
 * Pure PCM synthesizer generating subtle, low-frequency (75-210 Hz) tactile HUD feedback
 * waveforms with smooth cosine edge windows to prevent speaker pops or clipping.
 */
object LowFreqPcmSynthesizer {
  const val SAMPLE_RATE = 22050

  fun synthesize(effect: HudSoundEffect, variant: Int = 0): ShortArray {
    return when (effect) {
      HudSoundEffect.TERMINAL_TYPE -> synthesizeTerminalType(variant)
      HudSoundEffect.TERMINAL_BACKSPACE -> synthesizeTerminalBackspace()
      HudSoundEffect.DATA_PROCESS -> synthesizeDataProcess()
      HudSoundEffect.DATA_BURST -> synthesizeDataBurst()
      HudSoundEffect.UI_SWITCH -> synthesizeUiSwitch()
      HudSoundEffect.COMMAND_EXEC -> synthesizeCommandExec()
      HudSoundEffect.ERROR_THUD -> synthesizeErrorThud()
      HudSoundEffect.BOOT_HUM -> synthesizeBootHum()
    }
  }

  private fun synthesizeTerminalType(variant: Int): ShortArray {
    val durationMs = 18
    val baseFreq = 116.0 + (variant.mod(4)) * 13.0 // 116..155 Hz low-frequency switch body
    return renderPcm(durationMs) { progress, t ->
      val freq = baseFreq * (1.0 - 0.32 * progress)
      val body = sin(2.0 * PI * freq * t) + 0.24 * sin(4.0 * PI * freq * t)
      val env = smoothEdgeWindow(progress, 0.14) * exp(-10.5 * progress)
      body * env * 0.068
    }
  }

  private fun synthesizeTerminalBackspace(): ShortArray {
    val durationMs = 20
    return renderPcm(durationMs) { progress, t ->
      val freq = 96.0 * (1.0 - 0.35 * progress)
      val body = sin(2.0 * PI * freq * t) + 0.18 * sin(3.0 * PI * freq * t)
      val env = smoothEdgeWindow(progress, 0.15) * exp(-9.5 * progress)
      body * env * 0.072
    }
  }

  private fun synthesizeDataProcess(): ShortArray {
    val durationMs = 64
    return renderPcm(durationMs) { progress, t ->
      // Two soft low-frequency telemetry processing pulses with subtle AM ripple
      val pulse =
        when {
          progress < 0.42 -> {
            val localP = progress / 0.42
            val wave = sin(2.0 * PI * 112.0 * t) + 0.20 * sin(2.0 * PI * 168.0 * t)
            wave * sin(PI * localP)
          }
          progress < 0.56 -> 0.0
          else -> {
            val localP = (progress - 0.56) / 0.44
            val wave = sin(2.0 * PI * 146.0 * t) + 0.18 * sin(2.0 * PI * 219.0 * t)
            wave * sin(PI * localP)
          }
        }
      val am = 0.85 + 0.15 * sin(2.0 * PI * 32.0 * t)
      pulse * am * smoothEdgeWindow(progress, 0.06) * 0.065
    }
  }

  private fun synthesizeDataBurst(): ShortArray {
    val durationMs = 46
    return renderPcm(durationMs) { progress, t ->
      val subSlot = (progress * 3.0)
      val slotIndex = subSlot.toInt().coerceIn(0, 2)
      val localP = subSlot - slotIndex
      val freq =
        when (slotIndex) {
          0 -> 124.0
          1 -> 152.0
          else -> 118.0
        }
      val gate = if (localP < 0.78) sin(PI * (localP / 0.78)) else 0.0
      sin(2.0 * PI * freq * t) * gate * smoothEdgeWindow(progress, 0.08) * 0.055
    }
  }

  private fun synthesizeUiSwitch(): ShortArray {
    val durationMs = 24
    return renderPcm(durationMs) { progress, t ->
      val freq = 148.0 + 36.0 * progress
      val wave = sin(2.0 * PI * freq * t) + 0.15 * sin(2.0 * PI * freq * 1.5 * t)
      wave * smoothEdgeWindow(progress, 0.22) * exp(-4.5 * progress) * 0.06
    }
  }

  private fun synthesizeCommandExec(): ShortArray {
    val durationMs = 66
    return renderPcm(durationMs) { progress, t ->
      val wave =
        if (progress < 0.44) {
          val lp = progress / 0.44
          (sin(2.0 * PI * 126.0 * t) + 0.2 * sin(2.0 * PI * 189.0 * t)) * sin(PI * lp)
        } else if (progress < 0.52) {
          0.0
        } else {
          val lp = (progress - 0.52) / 0.48
          (sin(2.0 * PI * 172.0 * t) + 0.2 * sin(2.0 * PI * 258.0 * t)) * sin(PI * lp)
        }
      wave * smoothEdgeWindow(progress, 0.06) * 0.075
    }
  }

  private fun synthesizeErrorThud(): ShortArray {
    val durationMs = 105
    return renderPcm(durationMs) { progress, t ->
      val wave = sin(2.0 * PI * 82.0 * t) + 0.45 * sin(2.0 * PI * 119.0 * t)
      wave * smoothEdgeWindow(progress, 0.08) * exp(-6.5 * progress) * 0.095
    }
  }

  private fun synthesizeBootHum(): ShortArray {
    val durationMs = 210
    return renderPcm(durationMs) { progress, t ->
      val freq =
        when {
          progress < 0.34 -> 98.0
          progress < 0.68 -> 130.8
          else -> 164.8
        }
      val segP = ((progress * 3.0) % 1.0)
      val wave = sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * freq * 1.5 * t)
      wave * (0.45 + 0.55 * sin(PI * segP)) * smoothEdgeWindow(progress, 0.08) * 0.072
    }
  }

  private fun smoothEdgeWindow(progress: Double, edgeFraction: Double): Double {
    return when {
      progress < edgeFraction -> 0.5 * (1.0 - cos(PI * (progress / edgeFraction)))
      progress > 1.0 - edgeFraction ->
        0.5 * (1.0 - cos(PI * ((1.0 - progress) / edgeFraction)))
      else -> 1.0
    }
  }

  private inline fun renderPcm(
    durationMs: Int,
    sampleFn: (progress: Double, timeSec: Double) -> Double,
  ): ShortArray {
    val numSamples = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(1)
    val buffer = ShortArray(numSamples)
    val denom = (numSamples - 1).coerceAtLeast(1).toDouble()
    for (i in 0 until numSamples) {
      val progress = i / denom
      val t = i.toDouble() / SAMPLE_RATE
      val sample = sampleFn(progress, t).coerceIn(-1.0, 1.0)
      buffer[i] =
        (sample * Short.MAX_VALUE)
          .toInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
    }
    return buffer
  }
}

/**
 * Sound manager for subtle, low-frequency HUD tactile audio triggered by UI interactions.
 * Pre-renders PCM buffers, rate-limits bursts, and guarantees AudioTrack lifecycle cleanup.
 */
class HudSoundManager {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val trackLimiter = Semaphore(permits = 3)
  private val typingVariant = AtomicInteger(0)
  private val lastPlayedAtMs = HashMap<HudSoundEffect, AtomicLong>(HudSoundEffect.entries.size).apply {
    HudSoundEffect.entries.forEach { put(it, AtomicLong(-10_000L)) }
  }

  private val typeVariants: Array<ShortArray> by lazy {
    Array(4) { idx -> LowFreqPcmSynthesizer.synthesize(HudSoundEffect.TERMINAL_TYPE, idx) }
  }
  private val cachedBuffers: Map<HudSoundEffect, ShortArray> by lazy {
    HudSoundEffect.entries
      .filter { it != HudSoundEffect.TERMINAL_TYPE }
      .associateWith { LowFreqPcmSynthesizer.synthesize(it) }
  }

  @Volatile var enabled: Boolean = true
  @Volatile private var released: Boolean = false

  fun playTerminalType() {
    if (released || !enabled || !allowRateLimit(HudSoundEffect.TERMINAL_TYPE, minGapMs = 24L)) return
    val idx = typingVariant.getAndIncrement().and(3)
    scope.launch { playBuffer(typeVariants[idx]) }
  }

  fun playTerminalBackspace() {
    play(HudSoundEffect.TERMINAL_BACKSPACE, minGapMs = 24L)
  }

  fun playDataProcess() {
    play(HudSoundEffect.DATA_PROCESS, minGapMs = 70L)
  }

  fun playDataBurst() {
    play(HudSoundEffect.DATA_BURST, minGapMs = 80L)
  }

  fun playUiSwitch() {
    play(HudSoundEffect.UI_SWITCH, minGapMs = 35L)
  }

  fun playCommandExec() {
    play(HudSoundEffect.COMMAND_EXEC, minGapMs = 60L)
  }

  fun playError() {
    play(HudSoundEffect.ERROR_THUD, minGapMs = 100L)
  }

  fun playBootHum() {
    play(HudSoundEffect.BOOT_HUM, minGapMs = 300L)
  }

  fun release() {
    released = true
    scope.cancel()
  }

  private fun play(effect: HudSoundEffect, minGapMs: Long) {
    if (released || !enabled || !allowRateLimit(effect, minGapMs)) return
    val buffer = cachedBuffers[effect] ?: return
    scope.launch { playBuffer(buffer) }
  }

  private fun allowRateLimit(effect: HudSoundEffect, minGapMs: Long): Boolean {
    val now = runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    val stamp = lastPlayedAtMs[effect] ?: return true
    val prev = stamp.get()
    if (now - prev < minGapMs) return false
    return stamp.compareAndSet(prev, now)
  }

  private suspend fun playBuffer(buffer: ShortArray) {
    if (released || buffer.isEmpty() || !trackLimiter.tryAcquire()) return
    try {
      val sampleRate = LowFreqPcmSynthesizer.SAMPLE_RATE
      val minBuf =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBuf <= 0) return
      val durationMs = ((buffer.size * 1000L) / sampleRate).coerceAtLeast(10L)
      val track =
        try {
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, buffer.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        } catch (_: Throwable) {
          return
        }

      try {
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        val written = track.write(buffer, 0, buffer.size)
        if (written <= 0) return
        track.play()
        delay(durationMs + 16L)
        runCatching { track.stop() }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        // Ignore transient audio device errors
      } finally {
        runCatching { track.release() }
      }
    } finally {
      trackLimiter.release()
    }
  }
}
