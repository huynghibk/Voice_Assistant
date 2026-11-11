package com.voiceassistant.engine.tts

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.tts.TextSplitter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Piper ONNX TTS with sentence queue + streaming emission.
 *
 * When a Piper `.onnx` model is present under assets/filesDir, runs real inference.
 * Otherwise falls back to [DemoToneSynthesizer] so the streaming pipeline is testable
 * before model packs are downloaded.
 */
@Singleton
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textSplitter: TextSplitter
) : TtsEngine {

    private val cancelled = AtomicBoolean(false)
    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionCache = mutableMapOf<String, OrtSession>()

    override fun synthesizeStreaming(text: String, voice: VoiceModel): Flow<PcmChunk> = flow {
        cancelled.set(false)
        val sentences = textSplitter.split(text)
        if (sentences.isEmpty()) return@flow

        val modelPath = resolveModelPath(voice.modelAssetPath)
        val useOnnx = modelPath != null

        sentences.forEachIndexed { index, sentence ->
            if (cancelled.get() || !coroutineContext.isActive) {
                throw CancellationException("TTS cancelled")
            }
            val pcm = if (useOnnx) {
                runCatching { inferWithOnnx(modelPath!!, sentence) }
                    .getOrElse { DemoToneSynthesizer.synthesize(sentence, SAMPLE_RATE) }
            } else {
                DemoToneSynthesizer.synthesize(sentence, SAMPLE_RATE)
            }
            // Short pause after sentence punctuation for natural prosody
            val withPause = appendSilence(pcm, (SAMPLE_RATE * 0.25).toInt())
            emit(
                PcmChunk(
                    sentenceIndex = index,
                    sentenceCount = sentences.size,
                    sentence = sentence,
                    pcm16le = withPause,
                    sampleRateHz = SAMPLE_RATE
                )
            )
        }
    }.flowOn(Dispatchers.Default)

    override fun cancel() {
        cancelled.set(true)
    }

    private fun resolveModelPath(assetPath: String?): String? {
        if (assetPath.isNullOrBlank()) return null
        val external = File(context.filesDir, assetPath)
        if (external.exists()) return external.absolutePath
        return runCatching {
            context.assets.open(assetPath).use { input ->
                val out = File(context.filesDir, assetPath)
                out.parentFile?.mkdirs()
                if (!out.exists()) {
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.absolutePath
            }
        }.getOrNull()
    }

    /**
     * Minimal ONNX invoke path. Real Piper models expect phoneme IDs;
     * once a concrete voice pack + phonemizer are bundled, replace the
     * placeholder input tensor construction below with Piper's phoneme pipeline.
     */
    private fun inferWithOnnx(modelPath: String, sentence: String): ShortArray {
        val session = sessionCache.getOrPut(modelPath) {
            ortEnv.createSession(modelPath, OrtSession.SessionOptions())
        }
        // Placeholder input — replaced when phonemizer + Piper config JSON are wired.
        // Until then, prefer DemoToneSynthesizer via the catch path if shapes mismatch.
        val inputName = session.inputNames.first()
        val tokens = FloatArray(sentence.length.coerceAtLeast(1)) { i ->
            (sentence.getOrElse(i) { ' ' }.code % 256) / 255f
        }
        val shape = longArrayOf(1, tokens.size.toLong())
        OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(tokens), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result.get(0).value
                return when (output) {
                    is Array<*> -> flattenFloatAudio(output)
                    is FloatArray -> floatToPcm16(output)
                    else -> DemoToneSynthesizer.synthesize(sentence, SAMPLE_RATE)
                }
            }
        }
    }

    private fun flattenFloatAudio(value: Array<*>): ShortArray {
        val floats = mutableListOf<Float>()
        fun walk(node: Any?) {
            when (node) {
                is FloatArray -> node.forEach { floats += it }
                is Array<*> -> node.forEach { walk(it) }
            }
        }
        walk(value)
        return floatToPcm16(floats.toFloatArray())
    }

    private fun floatToPcm16(data: FloatArray): ShortArray =
        ShortArray(data.size) { i ->
            (data[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

    private fun appendSilence(pcm: ShortArray, silenceSamples: Int): ShortArray {
        if (silenceSamples <= 0) return pcm
        return pcm + ShortArray(silenceSamples)
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}

/**
 * Development fallback: generates soft tonal PCM proportional to sentence length
 * so queue + AudioTrack streaming can be verified without ONNX voice packs.
 */
object DemoToneSynthesizer {
    fun synthesize(sentence: String, sampleRate: Int): ShortArray {
        val durationSec = (0.35 + sentence.length * 0.035).coerceIn(0.4, 4.0)
        val totalSamples = (durationSec * sampleRate).toInt()
        val baseFreq = 180.0 + (sentence.hashCode().and(0xFF) % 80)
        return ShortArray(totalSamples) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < sampleRate * 0.02 -> i / (sampleRate * 0.02)
                i > totalSamples - sampleRate * 0.05 ->
                    ((totalSamples - i) / (sampleRate * 0.05)).coerceAtLeast(0.0)
                else -> 1.0
            }
            val sample = 0.18 * sin(2.0 * PI * baseFreq * t) *
                (0.6 + 0.4 * sin(2.0 * PI * 3.0 * t))
            (sample * envelope * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
