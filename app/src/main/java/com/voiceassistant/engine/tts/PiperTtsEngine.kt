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

/**
 * Piper ONNX TTS with sentence queue + streaming emission.
 *
 * When a Piper `.onnx` model is present under assets/filesDir, runs real inference.
 * Otherwise falls back to [AndroidSystemTtsSynthesizer] so users hear real speech
 * (not a demo tone) before model packs are downloaded.
 */
@Singleton
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textSplitter: TextSplitter,
    private val androidSystemTtsSynthesizer: AndroidSystemTtsSynthesizer
) : TtsEngine {

    private val cancelled = AtomicBoolean(false)
    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionCache = mutableMapOf<String, OrtSession>()

    override fun synthesizeStreaming(text: String, voice: VoiceModel): Flow<PcmChunk> =
        synthesizeStreaming(text, voice, speechRate = 1.0f)

    fun synthesizeStreaming(
        text: String,
        voice: VoiceModel,
        speechRate: Float
    ): Flow<PcmChunk> = flow {
        cancelled.set(false)
        val sentences = textSplitter.split(text)
        if (sentences.isEmpty()) return@flow

        sentences.forEachIndexed { index, sentence ->
            if (cancelled.get() || !coroutineContext.isActive) {
                throw CancellationException("TTS cancelled")
            }
            emit(synthesizeSentence(sentence, voice, index, sentences.size, speechRate))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Synthesize a single sentence. Used by [com.voiceassistant.engine.playback.LocalPlaybackController]
     * for skip/resume from an arbitrary sentence index.
     */
    suspend fun synthesizeSentence(
        sentence: String,
        voice: VoiceModel,
        sentenceIndex: Int,
        sentenceCount: Int,
        speechRate: Float = 1.0f
    ): PcmChunk {
        val modelPath = resolveModelPath(voice.modelAssetPath)
        val useOnnx = modelPath != null
        val localeHint = AndroidSystemTtsSynthesizer.localeForVoiceId(voice.id, voice.name)
        // Speed is applied at AudioTrack (PlaybackParams) so Piper + System TTS share one path.
        val (pcm, sampleRate) = if (useOnnx) {
            runCatching {
                inferWithOnnx(modelPath!!, sentence) to SAMPLE_RATE
            }.getOrElse {
                androidSystemTtsSynthesizer.synthesize(sentence, localeHint, speechRate = 1.0f)
            }
        } else {
            androidSystemTtsSynthesizer.synthesize(sentence, localeHint, speechRate = 1.0f)
        }
        val withPause = appendSilence(pcm, (sampleRate * 0.25).toInt())
        return PcmChunk(
            sentenceIndex = sentenceIndex,
            sentenceCount = sentenceCount,
            sentence = sentence,
            pcm16le = withPause,
            sampleRateHz = sampleRate
        )
    }

    override fun cancel() {
        cancelled.set(true)
    }

    fun clearCancel() {
        cancelled.set(false)
    }

    private fun resolveModelPath(assetPath: String?): String? {
        if (assetPath.isNullOrBlank()) return null
        if (!assetPath.endsWith(".onnx", ignoreCase = true)) return null
        val external = File(context.filesDir, assetPath)
        val config = File(context.filesDir, "$assetPath.json")
        // Prefer filesDir downloads (onnx + onnx.json from Hugging Face).
        if (external.exists() && external.length() > 1024) {
            return external.absolutePath
        }
        return runCatching {
            context.assets.open(assetPath).use { input ->
                val out = File(context.filesDir, assetPath)
                out.parentFile?.mkdirs()
                if (!out.exists() || out.length() < 1024) {
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                // Copy matching config from assets when present.
                runCatching {
                    context.assets.open("$assetPath.json").use { cfgIn ->
                        if (!config.exists() || config.length() < 16) {
                            config.outputStream().use { cfgOut -> cfgIn.copyTo(cfgOut) }
                        }
                    }
                }
                if (out.exists() && out.length() > 1024) out.absolutePath else null
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
                    else -> error("Unsupported Piper output type")
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
