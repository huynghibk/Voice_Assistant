package com.voiceassistant.engine.audio

import kotlin.math.abs

object PcmSamples {

    fun floatToPcm16(data: FloatArray): ShortArray =
        ShortArray(data.size) { i ->
            (data[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
        }

    fun pcm16ToFloat(data: ShortArray): FloatArray =
        FloatArray(data.size) { i -> data[i] / 32767.0f }

    fun appendSilenceFloat(pcm: FloatArray, silenceSamples: Int): FloatArray {
        if (silenceSamples <= 0) return pcm
        return pcm + FloatArray(silenceSamples)
    }

    // Peak-normalize and limit to reduce clipping / “cassette” crackle on playback.
    fun normalizeForPlayback(data: FloatArray, targetPeak: Float = 0.92f): FloatArray {
        if (data.isEmpty()) return data
        var peak = 0f
        for (s in data) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        if (peak < 1e-8f) return data
        val gain = when {
            peak > 1.05f -> targetPeak / peak
            peak < 0.12f -> (targetPeak / peak).coerceAtMost(6f)
            else -> 1f
        }
        return FloatArray(data.size) { i ->
            (data[i] * gain).coerceIn(-1f, 1f)
        }
    }

    // Sherpa occasionally returns samples outside [-1, 1] or wrong scale.
    fun prepareModelOutput(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        val scaled = when {
            peak > 4f -> FloatArray(samples.size) { (samples[it] / 32768f).coerceIn(-1f, 1f) }
            else -> samples
        }
        return normalizeForPlayback(scaled)
    }
}
