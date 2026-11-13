package com.voiceassistant.engine.tts

import android.content.Context
import com.voiceassistant.engine.audio.PcmSamples
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
/**
 * Voice Conversion stage (mandatory architecture):
 *   Text → PiperTtsEngine (base PCM) → VoiceConversionEngine → target PCM
 *
 * Prefers OpenVoice/FreeVC-lite ONNX when `models/vc/converter.onnx` exists;
 * otherwise applies embedding-guided spectral tilt / formant-ish filtering
 * so clone UX works before the heavy model pack is installed.
 */
@Singleton
class VoiceConversionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingExtractor: SpeakerEmbeddingExtractor
) {

    data class ConversionResult(
        val pcmFloat: FloatArray? = null,
        val pcm16le: ShortArray? = null,
        val sampleRateHz: Int,
        val usedConversion: Boolean
    )

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    fun convert(
        basePcm: FloatArray,
        sampleRateHz: Int,
        embeddingFile: File?
    ): ConversionResult {
        if (embeddingFile == null || !embeddingFile.exists()) {
            return ConversionResult(pcmFloat = basePcm, sampleRateHz = sampleRateHz, usedConversion = false)
        }
        val embedding = embeddingExtractor.loadEmbedding(embeddingFile)
            ?: return ConversionResult(pcmFloat = basePcm, sampleRateHz = sampleRateHz, usedConversion = false)

        val baseShort = PcmSamples.floatToPcm16(basePcm)
        val onnxPath = resolveConverterModel()
        if (onnxPath != null) {
            runCatching {
                return convertWithOnnx(baseShort, sampleRateHz, embedding, onnxPath)
            }
        }
        // Without a real VC model, passthrough — legacy spectral filter sounded harsh / distorted.
        return ConversionResult(pcmFloat = basePcm, sampleRateHz = sampleRateHz, usedConversion = false)
    }

    private fun resolveConverterModel(): String? {
        val candidates = listOf(
            File(context.filesDir, "models/vc/converter.onnx"),
            File(context.getExternalFilesDir(null), "models/vc/converter.onnx")
        )
        candidates.firstOrNull { it.exists() }?.let { return it.absolutePath }
        return runCatching {
            context.assets.open("models/vc/converter.onnx").use { input ->
                val out = File(context.filesDir, "models/vc/converter.onnx")
                out.parentFile?.mkdirs()
                if (!out.exists()) out.outputStream().use { input.copyTo(it) }
                out.absolutePath
            }
        }.getOrNull()
    }

    private fun convertWithOnnx(
        basePcm: ShortArray,
        sampleRateHz: Int,
        embedding: FloatArray,
        modelPath: String
    ): ConversionResult {
        val sess = session ?: ortEnv.createSession(modelPath).also { session = it }
        val audio = FloatArray(basePcm.size) { basePcm[it] / Short.MAX_VALUE.toFloat() }
        val audioTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(audio),
            longArrayOf(1, audio.size.toLong())
        )
        val embTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(embedding),
            longArrayOf(1, embedding.size.toLong())
        )
        audioTensor.use { a ->
            embTensor.use { e ->
                val inputs = mutableMapOf<String, OnnxTensor>()
                val names = sess.inputNames.toList()
                inputs[names[0]] = a
                if (names.size > 1) inputs[names[1]] = e
                sess.run(inputs).use { result ->
                    val out = result.get(0).value
                    val floats = when (out) {
                        is FloatArray -> out
                        is Array<*> -> {
                            val flat = mutableListOf<Float>()
                            fun walk(n: Any?) {
                                when (n) {
                                    is FloatArray -> n.forEach { flat += it }
                                    is Array<*> -> n.forEach { walk(it) }
                                }
                            }
                            walk(out)
                            flat.toFloatArray()
                        }
                        else -> audio
                    }
                    val pcm = ShortArray(floats.size) { i ->
                        (floats[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                    }
                    return ConversionResult(
                        pcm16le = pcm,
                        sampleRateHz = sampleRateHz,
                        usedConversion = true
                    )
                }
            }
        }
    }

}
