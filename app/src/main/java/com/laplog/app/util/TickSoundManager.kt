package com.laplog.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.laplog.app.model.TickSoundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class TickSoundManager {
    private val sampleRate = 22050

    private val soundBuffers: Map<TickSoundType, ShortArray> =
        TickSoundType.entries.associateWith { generateSamples(it) }

    private fun generateSamples(type: TickSoundType): ShortArray = when (type) {
        TickSoundType.BUZZ    -> generateBuzz()
        TickSoundType.DRUM    -> generateDrum()
        TickSoundType.SOFT    -> generateSoft()
        TickSoundType.CHIME2  -> generateBell(freq = 1300.0, durationMs = 3000, volume = 0.50f, decayRate = 0.8, fadeStartSec = 2.0)
        TickSoundType.GONG    -> generateGong()
        TickSoundType.BOWL    -> generateBowl()
        TickSoundType.WHISTLE -> generateWhistle()
        else -> generateSine(type)
    }

    private fun generateSine(type: TickSoundType): ShortArray {
        val (freq, durationMs, volume) = when (type) {
            TickSoundType.TICK  -> Triple(1000.0,  40, 0.70f)
            TickSoundType.TOCK  -> Triple( 640.0,  60, 0.85f)
            TickSoundType.BELL  -> Triple(1400.0, 200, 0.60f)
            TickSoundType.DEEP  -> Triple( 280.0, 100, 0.95f)
            TickSoundType.HIGH  -> Triple(2000.0,  30, 0.65f)
            TickSoundType.WOOD  -> Triple( 800.0,  50, 0.90f)
            TickSoundType.BEEP  -> Triple(1200.0,  80, 0.75f)
            TickSoundType.PING  -> Triple(1800.0, 150, 0.55f)
            TickSoundType.SNAP  -> Triple(1600.0,  15, 1.00f)
            TickSoundType.CHIRP -> Triple(2500.0,  20, 0.55f)
            TickSoundType.CHIME -> Triple(1700.0, 350, 0.45f)
            else -> Triple(1000.0, 40, 0.70f)
        }
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val angleIncrement = 2.0 * PI * freq / sampleRate
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.05) t / 0.05 else exp(-8.0 * (t - 0.05))
            val sample = (sin(angleIncrement * i) * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Kick drum: pitch sweep 200→50 Hz + noise click at attack
    private fun generateDrum(): ShortArray {
        val durationMs = 180
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val random = java.util.Random(42)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val freq = 200.0 + (50.0 - 200.0) * t  // sweep 200→50 Hz
            phase += 2.0 * PI * freq / sampleRate
            val envelope = if (t < 0.02) t / 0.02 else exp(-7.0 * (t - 0.02))
            val noise = if (t < 0.04) (random.nextDouble() * 2 - 1) * 0.35 else 0.0
            val sample = ((sin(phase) + noise) * envelope * 0.95 * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Buzz: square-wave approximation via odd harmonics — harsh, horn-like
    private fun generateBuzz(): ShortArray {
        val freq = 160.0
        val durationMs = 150
        val volume = 0.80f
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val omega = 2.0 * PI * freq / sampleRate
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.03) t / 0.03 else exp(-5.0 * (t - 0.03))
            // Square-wave approximation (odd harmonics)
            val wave = sin(omega * i) +
                       0.333 * sin(3 * omega * i) +
                       0.200 * sin(5 * omega * i) +
                       0.143 * sin(7 * omega * i)
            val sample = (wave / 1.676 * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Bell with configurable frequency, slow decay, optional linear fadeout
    private fun generateBell(freq: Double, durationMs: Int, volume: Float, decayRate: Double, fadeStartSec: Double = -1.0): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val omega = 2.0 * PI * freq / sampleRate
        val totalSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val secs = i.toDouble() / sampleRate
            val naturalEnv = if (secs < 0.02) secs / 0.02 else exp(-decayRate * (secs - 0.02))
            val fadeEnv = if (fadeStartSec < 0 || secs < fadeStartSec) 1.0
                          else (1.0 - (secs - fadeStartSec) / (totalSec - fadeStartSec)).coerceAtLeast(0.0)
            val sample = (sin(omega * i) * naturalEnv * fadeEnv * volume * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Gong: multi-partial resonant bell, natural decay + linear fadeout after 2 s
    private fun generateGong(): ShortArray {
        val durationMs = 3000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val partials = listOf(200.0 to 0.70f, 520.0 to 0.40f, 940.0 to 0.22f, 1480.0 to 0.10f)
        val fadeStartSec = 2.0
        val totalSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val secs = i.toDouble() / sampleRate
            val naturalEnv = if (secs < 0.01) secs / 0.01 else exp(-1.2 * (secs - 0.01))
            val fadeEnv = if (secs < fadeStartSec) 1.0
                          else (1.0 - (secs - fadeStartSec) / (totalSec - fadeStartSec)).coerceAtLeast(0.0)
            val envelope = naturalEnv * fadeEnv
            var wave = 0.0
            for ((freq, amp) in partials) {
                wave += sin(2.0 * PI * freq / sampleRate * i) * amp
            }
            val sample = (wave / 1.42 * envelope * 0.65f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Soft tone: slow sine at 600 Hz, natural decay + linear fadeout after 2 s
    private fun generateSoft(): ShortArray {
        val durationMs = 3000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val omega = 2.0 * PI * 600.0 / sampleRate
        val fadeStartSec = 2.0
        val totalSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val secs = i.toDouble() / sampleRate
            val naturalEnv = if (secs < 0.05) secs / 0.05 else exp(-1.5 * (secs - 0.05))
            val fadeEnv = if (secs < fadeStartSec) 1.0
                          else (1.0 - (secs - fadeStartSec) / (totalSec - fadeStartSec)).coerceAtLeast(0.0)
            val sample = (sin(omega * i) * naturalEnv * fadeEnv * 0.38f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Singing bowl: fundamental + harmonics, natural decay + linear fadeout after 2 s
    private fun generateBowl(): ShortArray {
        val durationMs = 3000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val partials = listOf(432.0 to 0.70f, 1200.0 to 0.30f, 2120.0 to 0.15f)
        val fadeStartSec = 2.0
        val totalSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val secs = i.toDouble() / sampleRate
            val naturalEnv = if (secs < 0.04) secs / 0.04 else exp(-1.5 * (secs - 0.04))
            val fadeEnv = if (secs < fadeStartSec) 1.0
                          else (1.0 - (secs - fadeStartSec) / (totalSec - fadeStartSec)).coerceAtLeast(0.0)
            val envelope = naturalEnv * fadeEnv
            var wave = 0.0
            for ((freq, amp) in partials) {
                wave += sin(2.0 * PI * freq / sampleRate * i) * amp
            }
            val sample = (wave / 1.15 * envelope * 0.55f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Referee whistle: high freq with tremolo, fadeout after 2 s
    private fun generateWhistle(): ShortArray {
        val durationMs = 3000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val freq = 2700.0
        val omega = 2.0 * PI * freq / sampleRate
        val tremoloRate = 2.0 * PI * 25.0 / sampleRate
        val fadeStartSec = 2.0
        val totalSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val secs = i.toDouble() / sampleRate
            val naturalEnv = if (secs < 0.02) secs / 0.02 else exp(-1.2 * (secs - 0.02))
            val fadeEnv = if (secs < fadeStartSec) 1.0
                          else (1.0 - (secs - fadeStartSec) / (totalSec - fadeStartSec)).coerceAtLeast(0.0)
            val tremolo = 1.0 + 0.12 * sin(tremoloRate * i)
            val sample = (sin(omega * i) * tremolo * naturalEnv * fadeEnv * 0.60f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    suspend fun play(soundType: TickSoundType) = withContext(Dispatchers.IO) {
        val samples = soundBuffers[soundType] ?: return@withContext

        var audioTrack: AudioTrack? = null
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            val durationMs = samples.size.toLong() * 1000L / sampleRate
            delay(durationMs + 50L)
        } catch (_: Exception) {
        } finally {
            try { audioTrack?.stop() } catch (_: Exception) {}
            try { audioTrack?.release() } catch (_: Exception) {}
        }
    }
}
