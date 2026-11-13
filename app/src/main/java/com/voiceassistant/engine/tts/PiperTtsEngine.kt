package com.voiceassistant.engine.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.voiceassistant.data.prefs.TtsPreferences
import com.voiceassistant.domain.tts.TtsBackend
import com.voiceassistant.engine.audio.PcmSamples
import com.voiceassistant.engine.resource.TtsModelReadiness
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.tts.MultiLanguageTtsPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val multiLanguageTtsPlanner: MultiLanguageTtsPlanner,
    private val voiceRepository: VoiceRepository,
    private val androidSystemTtsSynthesizer: AndroidSystemTtsSynthesizer,
    private val ttsPreferences: TtsPreferences,
    private val googleCloudTtsSynthesizer: GoogleCloudTtsSynthesizer,
    private val elevenLabsTtsSynthesizer: ElevenLabsTtsSynthesizer
) : TtsEngine {

    private val cancelled = AtomicBoolean(false)
    private val ttsMutex = Mutex()
    private val ttsCache = mutableMapOf<String, OfflineTts>()
    private var espeakDataDirCache: String? = null

    override fun synthesizeStreaming(text: String, voice: VoiceModel): Flow<PcmChunk> =
        synthesizeStreaming(text, voice, speechRate = 1.0f)

    fun synthesizeStreaming(
        text: String,
        voice: VoiceModel,
        speechRate: Float
    ): Flow<PcmChunk> = flow {
        cancelled.set(false)
        val catalog = voiceRepository.listVoices()
        val planned = multiLanguageTtsPlanner.plan(text, voice, catalog)
        if (planned.isEmpty()) return@flow

        planned.forEachIndexed { index, item ->
            if (cancelled.get() || !coroutineContext.isActive) {
                throw CancellationException("TTS cancelled")
            }
            emit(
                synthesizeSentence(
                    sentence = item.text,
                    voice = item.voice,
                    sentenceIndex = index,
                    sentenceCount = planned.size,
                    speechRate = speechRate,
                    pauseAfterMs = item.pauseAfterMs
                )
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun synthesizeSentence(
        sentence: String,
        voice: VoiceModel,
        sentenceIndex: Int,
        sentenceCount: Int,
        speechRate: Float = 1.0f,
        pauseAfterMs: Int = 250
    ): PcmChunk {
        val localeHint = AndroidSystemTtsSynthesizer.localeForVoiceId(voice.id, voice.name)
        val isVietnamese = localeHint.language.equals("vi", ignoreCase = true)
        val backend = ttsPreferences.currentBackend()

        val (pcm, sampleRate) = try {
            when (backend) {
                TtsBackend.SYSTEM_GOOGLE -> synthesizeWithSystem(sentence, localeHint, speechRate, isVietnamese)
                TtsBackend.CLOUD_GOOGLE -> googleCloudTtsSynthesizer.synthesize(sentence, localeHint, speechRate)
                TtsBackend.CLOUD_ELEVENLABS -> elevenLabsTtsSynthesizer.synthesize(sentence, localeHint, speechRate)
                TtsBackend.OFFLINE_ONNX -> {
                    val modelPath = resolveModelPath(voice.modelAssetPath)
                    if (modelPath != null) {
                        val (raw, rate) = inferWithSherpaPiper(modelPath, sentence, speechRate)
                        PcmSamples.prepareModelOutput(raw) to rate
                    } else {
                        synthesizeWithSystem(sentence, localeHint, speechRate, isVietnamese)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            when (t.message) {
                "SYSTEM_TTS_VIETNAMESE_UNSUPPORTED" -> throw t
                "MISSING_GOOGLE_CLOUD_TTS_KEY" ->
                    throw IllegalStateException("TTS_FAILED: Thiếu Google Cloud TTS API key trong Cài đặt.")
                "MISSING_ELEVENLABS_KEY" ->
                    throw IllegalStateException("TTS_FAILED: Thiếu ElevenLabs API key trong Cài đặt.")
                else -> throw IllegalStateException("TTS_FAILED: ${t.message ?: t.javaClass.simpleName}")
            }
        }

        val silenceSamples = (sampleRate * (pauseAfterMs.coerceIn(80, 400) / 1000f)).toInt()
        val withPause = if (pcm.isEmpty()) {
            pcm
        } else {
            PcmSamples.appendSilenceFloat(pcm, silenceSamples)
        }
        return PcmChunk(
            sentenceIndex = sentenceIndex,
            sentenceCount = sentenceCount,
            sentence = sentence,
            pcmFloat = withPause,
            sampleRateHz = sampleRate
        )
    }

    override fun cancel() {
        cancelled.set(true)
        androidSystemTtsSynthesizer.stopSpeaking()
    }

    fun clearCancel() {
        cancelled.set(false)
    }

    private suspend fun inferWithSherpaPiper(
        modelPath: String,
        sentence: String,
        speechRate: Float
    ): Pair<FloatArray, Int> = ttsMutex.withLock {
        val kind = modelKindFor(modelPath)
        val tts = ttsCache.getOrPut(modelPath) {
            buildOfflineTts(modelPath, kind)
        }

        val engineRate = tts.sampleRate()
        val cleanText = sentence.trim()
        if (cleanText.isEmpty()) return@withLock FloatArray(0) to engineRate

        val speed = speechRate.coerceIn(0.5f, 2.5f)
        val cancelledRef = cancelled
        val audio = tts.generateWithCallback(
            text = cleanText,
            sid = 0,
            speed = speed
        ) { _ ->
            if (cancelledRef.get()) 0 else 1
        }
        val outRate = audio.sampleRate.takeIf { it > 0 } ?: engineRate
        audio.samples to outRate
    }

    private suspend fun synthesizeWithSystem(
        sentence: String,
        localeHint: java.util.Locale,
        speechRate: Float,
        isVietnamese: Boolean
    ): Pair<FloatArray, Int> {
        val result = androidSystemTtsSynthesizer.synthesize(sentence, localeHint, speechRate = speechRate)
        if (result.first.isEmpty() && isVietnamese) {
            throw IllegalStateException("SYSTEM_TTS_VIETNAMESE_UNSUPPORTED")
        }
        val floats = if (result.first.isEmpty()) {
            FloatArray(0)
        } else {
            PcmSamples.normalizeForPlayback(PcmSamples.pcm16ToFloat(result.first))
        }
        return floats to result.second
    }

    private fun buildOfflineTts(modelPath: String, kind: SherpaVitsKind): OfflineTts {
        val modelFile = File(modelPath)
        require(modelFile.exists() && modelFile.length() > 10_000L) {
            "File model .onnx bị lỗi hoặc chưa tải xong (${modelFile.length()} bytes)."
        }
        val parent = modelFile.parentFile ?: error("Thiếu thư mục chứa model ONNX")
        val jsonFile = File("$modelPath.json")

        when (kind) {
            SherpaVitsKind.PIPER -> {
                ensurePiperOnnxMetadata(modelFile, jsonFile)
            }
            SherpaVitsKind.MMS, SherpaVitsKind.MELO -> Unit
        }

        val tokensPath = when (kind) {
            SherpaVitsKind.PIPER -> ensureTokensFileGenerated(modelPath).absolutePath
            SherpaVitsKind.MMS, SherpaVitsKind.MELO -> {
                val tokens = File(parent, "tokens.txt")
                require(tokens.exists() && tokens.length() > 5) {
                    "Thiếu tokens.txt cho $modelPath"
                }
                tokens.absolutePath
            }
        }

        val dataDir = when (kind) {
            SherpaVitsKind.PIPER -> ensureEspeakDataExtracted()
            SherpaVitsKind.MMS, SherpaVitsKind.MELO -> ""
        }

        val lexiconPath = when (kind) {
            SherpaVitsKind.MELO -> {
                val lex = File(parent, "lexicon.txt")
                require(lex.exists() && lex.length() > 100) { "Thiếu lexicon.txt cho Melo TTS" }
                lex.absolutePath
            }
            SherpaVitsKind.PIPER -> resolveLexiconPath(modelPath)
            SherpaVitsKind.MMS -> ""
        }

        val dictDir = when (kind) {
            SherpaVitsKind.MELO -> File(parent, "dict").takeIf { it.isDirectory }?.absolutePath.orEmpty()
            else -> ""
        }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            lexicon = lexiconPath,
            tokens = tokensPath,
            dataDir = dataDir,
            dictDir = dictDir,
            noiseScale = DEFAULT_NOISE_SCALE,
            noiseScaleW = DEFAULT_NOISE_SCALE_W,
            lengthScale = DEFAULT_LENGTH_SCALE
        )
        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = 1,
            debug = false,
            provider = "cpu"
        )
        val ruleFsts = when (kind) {
            SherpaVitsKind.MELO -> listOf("date.fst", "number.fst", "phone.fst")
                .mapNotNull { name ->
                    File(parent, name).takeIf { it.exists() && it.length() > 0 }?.absolutePath
                }
                .joinToString(",")
            else -> ""
        }
        return OfflineTts(config = OfflineTtsConfig(model = modelConfig, ruleFsts = ruleFsts))
    }

    private enum class SherpaVitsKind { PIPER, MMS, MELO }

    private fun modelKindFor(modelPath: String): SherpaVitsKind {
        val lower = modelPath.lowercase()
        return when {
            "melo" in lower -> SherpaVitsKind.MELO
            "mms" in lower -> SherpaVitsKind.MMS
            else -> SherpaVitsKind.PIPER
        }
    }

    private fun resolveLexiconPath(modelPath: String): String {
        val lexicon = File("$modelPath.lexicon.txt")
        if (lexicon.exists() && lexicon.length() > 0) return lexicon.absolutePath
        val alt = File(File(modelPath).parentFile, "lexicon.txt")
        return if (alt.exists() && alt.length() > 0) alt.absolutePath else ""
    }

    // sherpa-onnx tokens: one UTF-8 codepoint + id (space uses id-only line). See piper.html.
    private fun ensureTokensFileGenerated(modelPath: String): File {
        val tokensFile = File("$modelPath.tokens_v4.txt")
        if (tokensFile.exists() && tokensFile.length() > 10) return tokensFile

        File("$modelPath.tokens.txt").takeIf { it.exists() && it.length() > 10 }?.let { return it }
        File("$modelPath.tokens_v2.txt").delete()
        File("$modelPath.tokens_v3.txt").delete()

        val jsonFile = File("$modelPath.json")
        require(jsonFile.exists() && jsonFile.length() > 50) {
            "Thiếu file cấu hình ${jsonFile.name}"
        }

        val root = JSONObject(jsonFile.readText())
        val mapObj = root.getJSONObject("phoneme_id_map")
        val pairs = mutableListOf<Pair<Int, String>>()
        val keys = mapObj.keys()
        while (keys.hasNext()) {
            val symbol = keys.next()
            if (symbol == "\n" || symbol == "\r") continue
            if (symbol.codePointCount(0, symbol.length) != 1) continue
            val idArray = mapObj.optJSONArray(symbol) ?: continue
            if (idArray.length() == 0) continue
            pairs.add(idArray.getInt(0) to symbol)
        }
        pairs.sortBy { it.first }
        val content = pairs.joinToString("\n") { (id, symbol) ->
            if (symbol == " ") "$id" else "$symbol $id"
        }
        tokensFile.writeText(content)
        return tokensFile
    }

    /**
     * Extracts assets/espeak-ng-data to filesDir and verifies required compiled tables exist.
     */
    private fun ensureEspeakDataExtracted(): String {
        espeakDataDirCache?.let { cached ->
            if (isEspeakDataValid(File(cached))) return cached
        }

        val targetDir = File(context.filesDir, "espeak-ng-data")
        if (!isEspeakDataValid(targetDir)) {
            targetDir.deleteRecursively()
            copyAssetFolder("espeak-ng-data", targetDir)
        }

        require(isEspeakDataValid(targetDir)) {
            espeakDataError(targetDir)
        }

        espeakDataDirCache = targetDir.absolutePath
        return targetDir.absolutePath
    }

    private fun isEspeakDataValid(dir: File): Boolean {
        val requiredFiles = listOf("phontab", "phonindex", "phondata", "intonations")
        if (!dir.exists() || requiredFiles.any { name ->
                val f = File(dir, name)
                !f.exists() || f.length() <= 0L
            }
        ) {
            return false
        }
        // Native espeak in sherpa-onnx 1.12.11 compares phondata against 0x014801
        // (MOVZ #0x4801 + MOVK #1, LSL #16) and aborts the process on mismatch.
        return readPhondataVersion(File(dir, "phondata")) == ESPEAK_PHONDATA_VERSION
    }

    private fun readPhondataVersion(phondata: File): Int =
        runCatching {
            phondata.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) < 4) return@use -1
                (header[0].toInt() and 0xff) or
                    ((header[1].toInt() and 0xff) shl 8) or
                    ((header[2].toInt() and 0xff) shl 16) or
                    ((header[3].toInt() and 0xff) shl 24)
            }
        }.getOrDefault(-1)

    private fun espeakDataError(dir: File): String {
        val found = readPhondataVersion(File(dir, "phondata"))
        return "Bộ espeak-ng-data không khớp sherpa-onnx " +
            "(phondata=0x${found.toString(16)}, cần 0x${ESPEAK_PHONDATA_VERSION.toString(16)}). " +
            "App dừng trước mã native để khỏi bị thoát. " +
            "Hãy thay assets/espeak-ng-data bằng gói đi kèm sherpa-onnx 1.12.11."
    }

    private fun ensurePiperOnnxMetadata(modelFile: File, jsonFile: File) {
        if (!jsonFile.exists()) return
        val marker = "va-piper-meta-v1"
        if (modelFile.containsBytes(marker.toByteArray())) return
        val root = JSONObject(jsonFile.readText())
        val sampleRate = root.optJSONObject("audio")?.optInt("sample_rate", SAMPLE_RATE)
            ?: SAMPLE_RATE
        val speakers = root.optInt("num_speakers", 1).coerceAtLeast(1)
        val voice = root.optJSONObject("espeak")?.optString("voice").orEmpty().ifBlank { "vi" }
        val language = root.optJSONObject("language")?.optString("code").orEmpty().ifBlank { voice }
        val entries = linkedMapOf(
            "model_type" to "vits",
            "comment" to marker,
            "language" to language,
            "voice" to voice,
            "has_espeak" to "1",
            "n_speakers" to speakers.toString(),
            "sample_rate" to sampleRate.toString()
        )
        FileOutputStream(modelFile, true).use { out ->
            out.write(onnxMetadataProps(entries))
        }
    }

    private fun File.containsBytes(needle: ByteArray): Boolean {
        if (needle.isEmpty() || length() < needle.size) return false
        inputStream().use { input ->
            val window = ByteArray(64 * 1024)
            var carry = ByteArray(0)
            while (true) {
                val n = input.read(window)
                if (n <= 0) return false
                val buf = if (carry.isEmpty()) window.copyOf(n) else carry + window.copyOf(n)
                if (buf.indexOfSlice(needle) >= 0) return true
                carry = if (buf.size > needle.size) buf.copyOfRange(buf.size - needle.size + 1, buf.size) else buf
            }
        }
    }

    private fun ByteArray.indexOfSlice(needle: ByteArray): Int {
        if (needle.size > size) return -1
        for (i in 0..size - needle.size) {
            var ok = true
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) {
                    ok = false
                    break
                }
            }
            if (ok) return i
        }
        return -1
    }

    private fun onnxMetadataProps(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((key, value) in entries) {
            val entry = ByteArrayOutputStream()
            writeProtoString(entry, 1, key)
            writeProtoString(entry, 2, value)
            val bytes = entry.toByteArray()
            writeVarint(out, (14 shl 3) or 2)
            writeVarint(out, bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun writeProtoString(out: ByteArrayOutputStream, field: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, (field shl 3) or 2)
        writeVarint(out, bytes.size)
        out.write(bytes)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7f.inv() != 0) {
            out.write((v and 0x7f) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val list = context.assets.list(assetPath) ?: return
        if (list.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            targetDir.mkdirs()
            for (child in list) {
                copyAssetFolder("$assetPath/$child", File(targetDir, child))
            }
        }
    }

    private fun resolveModelPath(assetPath: String?): String? {
        if (assetPath.isNullOrBlank()) return null
        if (!assetPath.endsWith(".onnx", ignoreCase = true)) return null
        val external = File(context.filesDir, assetPath)
        val jsonPath = "${assetPath}.json"
        val externalJson = File(context.filesDir, jsonPath)
        ensureAssetCopied(jsonPath, externalJson)
        ensureAssetCopied("${assetPath}.lexicon.txt", File(context.filesDir, "${assetPath}.lexicon.txt"))
        ensureAssetCopied("${assetPath}.tokens.txt", File(context.filesDir, "${assetPath}.tokens.txt"))
        if (isOnnxModelUsable(external, externalJson)) {
            return external.absolutePath
        }
        return runCatching {
            context.assets.open(assetPath).use { input ->
                val out = File(context.filesDir, assetPath)
                out.parentFile?.mkdirs()
                if (!out.exists() || out.length() < MIN_PIPER_ONNX_BYTES) {
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                ensureAssetCopied(jsonPath, externalJson)
                ensureAssetCopied("${assetPath}.lexicon.txt", File(context.filesDir, "${assetPath}.lexicon.txt"))
                ensureAssetCopied("${assetPath}.tokens.txt", File(context.filesDir, "${assetPath}.tokens.txt"))
                if (isOnnxModelUsable(out, externalJson)) out.absolutePath else null
            }
        }.getOrNull()
    }

    private fun ensureAssetCopied(assetRelativePath: String, target: File) {
        if (target.exists() && target.length() > 0) return
        runCatching {
            context.assets.open(assetRelativePath).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun isOnnxModelUsable(onnx: File, json: File): Boolean =
        TtsModelReadiness.isOnnxReady(onnx)

    companion object {
        const val SAMPLE_RATE = 22_050
        private const val DEFAULT_NOISE_SCALE = 0.45f
        private const val DEFAULT_NOISE_SCALE_W = 0.65f
        private const val DEFAULT_LENGTH_SCALE = 1.0f
        /** Partial Piper downloads must not be passed to native OfflineTts (process kill). */
        private const val MIN_PIPER_ONNX_BYTES = 512L * 1024L
        /** Little-endian phondata header required by espeak inside sherpa-onnx 1.12.11. */
        private const val ESPEAK_PHONDATA_VERSION = 0x00014801
    }
}
