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
        TickSoundType.CHIME2  -> generateBell(freq = 1300.0, durationMs = 700,  volume = 0.50f, decayRate = 3.0)
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
            TickSoundType.SOFT  -> Triple( 600.0,  35, 0.38f)
            TickSoundType.SNAP  -> Triple(1600.0,  15, 1.00f)
            TickSoundType.CHIRP -> Triple(2500.0,  20, 0.55f)
            TickSoundType.DRUM  -> Triple( 100.0, 150, 0.95f)
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

    // Bell with configurable frequency and slow decay
    private fun generateBell(freq: Double, durationMs: Int, volume: Float, decayRate: Double): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val omega = 2.0 * PI * freq / sampleRate
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.02) t / 0.02 else exp(-decayRate * (t - 0.02))
            val sample = (sin(omega * i) * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Gong: multi-partial resonant bell with very slow decay
    private fun generateGong(): ShortArray {
        val durationMs = 1500
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        // Fundamental + inharmonic partials typical of struck metal
        val partials = listOf(200.0 to 0.70f, 520.0 to 0.40f, 940.0 to 0.22f, 1480.0 to 0.10f)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.01) t / 0.01 else exp(-2.0 * (t - 0.01))
            var wave = 0.0
            for ((freq, amp) in partials) {
                wave += sin(2.0 * PI * freq / sampleRate * i) * amp
            }
            val sample = (wave / 1.42 * envelope * 0.65f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Singing bowl: fundamental + harmonics, very slow decay (~2 s)
    private fun generateBowl(): ShortArray {
        val durationMs = 2000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val partials = listOf(432.0 to 0.70f, 1200.0 to 0.30f, 2120.0 to 0.15f)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.04) t / 0.04 else exp(-1.5 * (t - 0.04))
            var wave = 0.0
            for ((freq, amp) in partials) {
                wave += sin(2.0 * PI * freq / sampleRate * i) * amp
            }
            val sample = (wave / 1.15 * envelope * 0.55f * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    // Referee whistle: high freq with tremolo, ~1 s
    private fun generateWhistle(): ShortArray {
        val durationMs = 1000
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)
        val freq = 2700.0
        val omega = 2.0 * PI * freq / sampleRate
        val tremoloRate = 2.0 * PI * 25.0 / sampleRate  // 25 Hz flutter
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = if (t < 0.02) t / 0.02 else exp(-2.5 * (t - 0.02))
            val tremolo = 1.0 + 0.12 * sin(tremoloRate * i)
            val sample = (sin(omega * i) * tremolo * envelope * 0.60f * Short.MAX_VALUE).toInt()
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
