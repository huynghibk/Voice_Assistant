package com.voiceassistant.engine.tts

import com.voiceassistant.engine.audio.readWavPcm16Mono
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extracts a compact speaker embedding from a short WAV sample.
 *
 * When an ONNX embedding model is present at models/vc/embedder.onnx it can be
 * wired in later; until then we use a robust spectral/prosody feature vector
 * (log-mel-ish bands + pitch + spectral tilt) suitable for FreeVC-style VC.
 */
@Singleton
class SpeakerEmbeddingExtractor @Inject constructor() {

    fun extractFromWav(sampleWav: File, outEmbedding: File): FloatArray {
        val (pcm, sampleRate) = sampleWav.readWavPcm16Mono()
        val embedding = extractFeatures(pcm, sampleRate)
        outEmbedding.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(outEmbedding)).use { out ->
            out.writeInt(embedding.size)
            val buf = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { buf.putFloat(it) }
            out.write(buf.array())
        }
        return embedding
    }

    fun loadEmbedding(file: File): FloatArray? {
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val size = buf.int
            FloatArray(size) { buf.float }
        }.getOrNull()
    }

    fun extractFeatures(pcm: ShortArray, sampleRate: Int): FloatArray {
        if (pcm.isEmpty()) return FloatArray(EMBED_DIM)
        val floats = FloatArray(pcm.size) { pcm[it] / Short.MAX_VALUE.toFloat() }

        val bands = melBandEnergies(floats, sampleRate, bandCount = 24)
        val pitch = estimatePitchHz(floats, sampleRate) / 400f
        val tilt = spectralTilt(floats)
        val rms = sqrt(floats.fold(0f) { a, v -> a + v * v } / floats.size)
        val zcr = zeroCrossingRate(floats)

        val features = FloatArray(EMBED_DIM)
        for (i in bands.indices) features[i] = bands[i]
        features[24] = pitch
        features[25] = tilt
        features[26] = rms
        features[27] = zcr
        // Pad / hash remaining dims for stable length expected by VC
        for (i in 28 until EMBED_DIM) {
            features[i] = features[i % 28] * 0.37f + (i % 7) * 0.01f
        }
        return l2Normalize(features)
    }

    private fun melBandEnergies(signal: FloatArray, sampleRate: Int, bandCount: Int): FloatArray {
        val frame = min(1024, signal.size)
        val hop = frame / 2
        val energies = FloatArray(bandCount)
        var frames = 0
        var pos = 0
        while (pos + frame <= signal.size) {
            for (b in 0 until bandCount) {
                val f0 = melToHz(hzToMel(80f) + b * (hzToMel(sampleRate / 2f) - hzToMel(80f)) / bandCount)
                val f1 = melToHz(hzToMel(80f) + (b + 1) * (hzToMel(sampleRate / 2f) - hzToMel(80f)) / bandCount)
                var e = 0.0
                for (n in 0 until frame) {
                    val w = 0.5 - 0.5 * cos(2.0 * PI * n / (frame - 1))
                    val x = signal[pos + n] * w
                    // Cheap band energy via Goertzel-ish single bin around center
                    val freq = (f0 + f1) / 2.0
                    val omega = 2.0 * PI * freq / sampleRate
                    e += abs(x * sin(omega * n))
                }
                energies[b] = energies[b] + ln(1.0 + e).toFloat()
            }
            frames++
            pos += hop
        }
        if (frames > 0) {
            for (i in energies.indices) {
                energies[i] = energies[i] / frames
            }
        }
        return energies
    }

    private fun estimatePitchHz(signal: FloatArray, sampleRate: Int): Float {
        val minLag = sampleRate / 400
        val maxLag = sampleRate / 60
        if (signal.size < maxLag * 2) return 120f
        var bestLag = minLag
        var bestCorr = Float.NEGATIVE_INFINITY
        val start = signal.size / 4
        val end = min(signal.size - maxLag, start + sampleRate)
        for (lag in minLag..maxLag) {
            var corr = 0f
            var n = 0
            var i = start
            while (i < end) {
                corr += signal[i] * signal[i + lag]
                n++
                i++
            }
            corr /= max(1, n)
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        return sampleRate.toFloat() / bestLag
    }

    private fun spectralTilt(signal: FloatArray): Float {
        val n = min(signal.size, 4096)
        var low = 0f
        var high = 0f
        for (i in 0 until n) {
            val v = abs(signal[i])
            if (i < n / 2) low += v else high += v
        }
        return ((high - low) / (high + low + 1e-6f))
    }

    private fun zeroCrossingRate(signal: FloatArray): Float {
        var zc = 0
        for (i in 1 until signal.size) {
            if ((signal[i - 1] >= 0 && signal[i] < 0) || (signal[i - 1] < 0 && signal[i] >= 0)) zc++
        }
        return zc.toFloat() / signal.size
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun hzToMel(hz: Float): Float = (2595f * ln(1f + hz / 700f) / ln(10f))
    private fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

    companion object {
        const val EMBED_DIM = 64
    }
}
