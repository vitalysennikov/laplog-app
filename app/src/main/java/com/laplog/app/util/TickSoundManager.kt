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
        TickSoundType.values()
            .filter { it != TickSoundType.SILENT }
            .associateWith { generateSamples(it) }

    private fun generateSamples(type: TickSoundType): ShortArray {
        val (freq, durationMs, volume) = when (type) {
            TickSoundType.TICK -> Triple(1000.0, 40, 0.70f)
            TickSoundType.TOCK -> Triple(640.0, 60, 0.85f)
            TickSoundType.BELL -> Triple(1400.0, 200, 0.60f)
            TickSoundType.DEEP -> Triple(280.0, 100, 0.95f)
            TickSoundType.SILENT -> Triple(0.0, 0, 0f)
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

    suspend fun play(soundType: TickSoundType) = withContext(Dispatchers.IO) {
        if (soundType == TickSoundType.SILENT) return@withContext
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
