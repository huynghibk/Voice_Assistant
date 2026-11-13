package com.voiceassistant.engine.resource

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.resource.MlKitLanguagePack
import com.voiceassistant.domain.resource.MlKitPairPack
import com.voiceassistant.domain.resource.PiperVoicePack
import com.voiceassistant.domain.resource.ResourcePackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ResourcePackManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceRepository: VoiceRepository
) : ResourcePackManager {

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }

    private val mlKitCatalog = listOf(
        MlKitLanguagePack(TranslateLanguage.ENGLISH, "English", 30),
        MlKitLanguagePack(TranslateLanguage.VIETNAMESE, "Tiếng Việt", 30),
        MlKitLanguagePack(TranslateLanguage.JAPANESE, "日本語", 35),
        MlKitLanguagePack(TranslateLanguage.KOREAN, "한국어", 35),
        MlKitLanguagePack(TranslateLanguage.CHINESE, "中文", 35),
        MlKitLanguagePack(TranslateLanguage.FRENCH, "Français", 30)
    )

    private val recommendedPairs = listOf(
        Triple(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.VIETNAMESE,
            "EN ↔ VI (2 chiều Anh - Việt)"
        ),
        Triple(TranslateLanguage.JAPANESE, TranslateLanguage.VIETNAMESE, "JA ↔ VI"),
        Triple(TranslateLanguage.KOREAN, TranslateLanguage.VIETNAMESE, "KO ↔ VI"),
        Triple(TranslateLanguage.CHINESE, TranslateLanguage.VIETNAMESE, "ZH ↔ VI"),
        Triple(TranslateLanguage.FRENCH, TranslateLanguage.VIETNAMESE, "FR ↔ VI")
    )

    private val piperCatalog = listOf(
        PiperVoicePack(
            id = "preset_vi_demo",
            displayName = "Tiếng Việt · MMS (vits-mms-vie)",
            modelAssetPath = "models/sherpa/vits-mms-vie/model.onnx",
            qualityLabel = "HQ",
            estimatedSizeMb = 103,
            // Symmetris repo uses ISO 639-3 folder names; Vietnamese is `vie` (not on HF yet).
            // Until upstream publishes `vie/`, use Piper 25h for on-device Vietnamese TTS.
            modelDownloadUrl = null,
            companionDownloadUrls = emptyMap()
        ),
        PiperVoicePack(
            id = "preset_vi_piper_25h",
            displayName = "Tiếng Việt · Piper 25h",
            modelAssetPath = "models/piper/vi_VN-25hours_single-low.onnx",
            qualityLabel = "Recommended",
            estimatedSizeMb = 64,
            sherpaTarballUrl = "$SHERPA_RELEASE/vits-piper-vi_VN-25hours_single-low.tar.bz2",
            modelDownloadUrl = "$HF_BASE/vi/vi_VN/25hours_single/low/vi_VN-25hours_single-low.onnx",
            configDownloadUrl = "$HF_BASE/vi/vi_VN/25hours_single/low/vi_VN-25hours_single-low.onnx.json"
        ),
        PiperVoicePack(
            id = "preset_zh_melo",
            displayName = "中文 · Melo (zh+en)",
            modelAssetPath = "models/sherpa/vits-melo-tts-zh_en/model.onnx",
            qualityLabel = "HQ",
            estimatedSizeMb = 170,
            sherpaTarballUrl = "$SHERPA_RELEASE/vits-melo-tts-zh_en.tar.bz2"
        ),
        PiperVoicePack(
            id = "preset_en_amy",
            displayName = "English · Amy",
            modelAssetPath = "models/piper/en_US-amy-low.onnx",
            qualityLabel = "Low",
            estimatedSizeMb = 63,
            modelDownloadUrl = "$HF_BASE/en/en_US/amy/low/en_US-amy-low.onnx",
            configDownloadUrl = "$HF_BASE/en/en_US/amy/low/en_US-amy-low.onnx.json"
        ),
        PiperVoicePack(
            id = "preset_en_ryan",
            displayName = "English · Ryan",
            modelAssetPath = "models/piper/en_US-ryan-low.onnx",
            qualityLabel = "Low",
            estimatedSizeMb = 63,
            modelDownloadUrl = "$HF_BASE/en/en_US/ryan/low/en_US-ryan-low.onnx",
            configDownloadUrl = "$HF_BASE/en/en_US/ryan/low/en_US-ryan-low.onnx.json"
        )
    )

    override suspend fun listPiperPacks(): List<PiperVoicePack> = withContext(Dispatchers.IO) {
        voiceRepository.seedPresetsIfEmpty()
        piperCatalog.map { pack ->
            pack.copy(isReady = isPiperPathReady(pack.modelAssetPath))
        }
    }

    override fun isPiperPathReady(modelAssetPath: String?): Boolean {
        if (modelAssetPath.isNullOrBlank()) return false
        if (!modelAssetPath.endsWith(".onnx", ignoreCase = true)) return false
        val external = File(context.filesDir, modelAssetPath)
        if (TtsModelReadiness.isOnnxReady(external)) return true
        return runCatching {
            context.assets.openFd(modelAssetPath).use { it.length > MIN_PIPER_ONNX_BYTES }
        }.getOrElse {
            runCatching {
                context.assets.open(modelAssetPath).use { input ->
                    var total = 0L
                    val buf = ByteArray(8 * 1024)
                    while (total < MIN_PIPER_ONNX_BYTES) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                    }
                    total >= MIN_PIPER_ONNX_BYTES
                }
            }.getOrDefault(false)
        }
    }

    override suspend fun anyPiperReady(): Boolean =
        listPiperPacks().any { it.isReady }

    override suspend fun downloadPiperVoice(packId: String) = withContext(Dispatchers.IO) {
        val pack = piperCatalog.firstOrNull { it.id == packId }
            ?: error("Không tìm thấy gói giọng $packId.")
        if (!pack.sherpaTarballUrl.isNullOrBlank()) {
            try {
                downloadSherpaTarball(pack)
                return@withContext
            } catch (e: Exception) {
                if (pack.modelDownloadUrl.isNullOrBlank()) throw e
            }
        }
        val modelUrl = pack.modelDownloadUrl
            ?: error("Gói ${pack.displayName} chưa hỗ trợ tải về (đang chờ bản ONNX trên Hugging Face).")
        val modelFile = File(context.filesDir, pack.modelAssetPath)
        modelFile.parentFile?.mkdirs()

        val modelTmp = File(modelFile.parentFile, "${modelFile.name}.part")
        val temps = mutableListOf(modelTmp)
        try {
            downloadToFile(modelUrl, modelTmp)
            if (modelTmp.length() < 1024L) {
                error("File model Piper tải về bị lỗi (quá nhỏ).")
            }
            if (modelFile.exists()) modelFile.delete()
            if (!modelTmp.renameTo(modelFile)) {
                modelTmp.copyTo(modelFile, overwrite = true)
                modelTmp.delete()
            }

            val configUrl = pack.configDownloadUrl
            if (!configUrl.isNullOrBlank()) {
                val configFile = File(context.filesDir, "${pack.modelAssetPath}.json")
                val configTmp = File(configFile.parentFile, "${configFile.name}.part")
                temps += configTmp
                downloadToFile(configUrl, configTmp)
                if (configTmp.length() < 16L) {
                    error("File cấu hình Piper tải về bị lỗi.")
                }
                if (configFile.exists()) configFile.delete()
                if (!configTmp.renameTo(configFile)) {
                    configTmp.copyTo(configFile, overwrite = true)
                    configTmp.delete()
                }
            }

            for ((name, url) in pack.companionDownloadUrls) {
                val companion = File(modelFile.parentFile, name)
                val companionTmp = File(companion.parentFile, "${companion.name}.part")
                temps += companionTmp
                downloadToFile(url, companionTmp)
                if (companionTmp.length() < 4L) {
                    error("File $name tải về bị lỗi.")
                }
                if (companion.exists()) companion.delete()
                if (!companionTmp.renameTo(companion)) {
                    companionTmp.copyTo(companion, overwrite = true)
                    companionTmp.delete()
                }
            }

            if (!TtsModelReadiness.isOnnxReady(modelFile)) {
                error("Gói ${pack.displayName} tải xong nhưng thiếu file đi kèm (tokens/lexicon).")
            }
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.setDefault(pack.id)
        } catch (e: Exception) {
            temps.forEach { it.delete() }
            throw e
        }
    }

    private suspend fun downloadSherpaTarball(pack: PiperVoicePack) {
        val url = pack.sherpaTarballUrl ?: error("Thiếu URL tarball Sherpa.")
        val modelFile = File(context.filesDir, pack.modelAssetPath)
        val destRoot = modelFile.parentFile ?: error("Thiếu thư mục đích.")
        destRoot.mkdirs()
        val tarTmp = File(destRoot.parentFile, "${destRoot.name}.tar.bz2.part")
        try {
            downloadToFile(url, tarTmp)
            if (tarTmp.length() < 1024L) {
                error("Gói Sherpa tải về bị lỗi (quá nhỏ).")
            }
            if (destRoot.exists()) destRoot.deleteRecursively()
            destRoot.mkdirs()
            SherpaTarballExtractor.extract(tarTmp, destRoot)
            if (!TtsModelReadiness.isOnnxReady(modelFile)) {
                error("Giải nén xong nhưng thiếu file model/token cho ${pack.displayName}.")
            }
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.setDefault(pack.id)
        } finally {
            tarTmp.delete()
        }
    }

    private fun downloadToFile(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        var connection: HttpURLConnection? = null
        try {
            connection = openFollowRedirects(url)
            val code = connection.responseCode
            if (code !in 200..299) {
                error("Tải thất bại (HTTP $code): $url")
            }
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                    }
                    output.flush()
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun openFollowRedirects(url: String, depth: Int = 0): HttpURLConnection {
        if (depth > 5) error("Quá nhiều chuyển hướng khi tải: $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VoiceAssistant/1.0")
            setRequestProperty("Accept", "*/*")
        }
        val code = connection.responseCode
        if (code in listOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location")
                ?: error("Chuyển hướng thiếu Location: $url")
            connection.disconnect()
            val next = if (location.startsWith("http")) location else URL(URL(url), location).toString()
            return openFollowRedirects(next, depth + 1)
        }
        return connection
    }

    override suspend fun listMlKitLanguages(): List<MlKitLanguagePack> =
        withContext(Dispatchers.IO) {
            val downloaded = runCatching {
                Tasks.await(modelManager.getDownloadedModels(TranslateRemoteModel::class.java))
                    .map { it.language }
                    .toSet()
            }.getOrDefault(emptySet())
            mlKitCatalog.map { pack ->
                pack.copy(isInstalled = pack.languageCode in downloaded)
            }
        }

    override suspend fun listRecommendedPairs(): List<MlKitPairPack> =
        withContext(Dispatchers.IO) {
            val langs = listMlKitLanguages().associateBy { it.languageCode }
            recommendedPairs.map { (src, tgt, label) ->
                val srcPack = langs[src]
                val tgtPack = langs[tgt]
                val installed = srcPack?.isInstalled == true && tgtPack?.isInstalled == true
                val size = (srcPack?.estimatedSizeMb ?: 30) + (tgtPack?.estimatedSizeMb ?: 30)
                MlKitPairPack(
                    sourceCode = src,
                    targetCode = tgt,
                    displayLabel = label,
                    estimatedSizeMb = size,
                    isInstalled = installed
                )
            }
        }

    override suspend fun isMlKitPairReady(sourceCode: String, targetCode: String): Boolean =
        withContext(Dispatchers.IO) {
            if (sourceCode == targetCode) return@withContext true
            val srcOk = isLanguageDownloaded(sourceCode)
            val tgtOk = isLanguageDownloaded(targetCode)
            srcOk && tgtOk
        }

    override suspend fun downloadMlKitLanguage(languageCode: String, requireWifi: Boolean) =
        withContext(Dispatchers.IO) {
            downloadOneMlKitModel(languageCode, requireWifi)
            // ML Kit pairs are bidirectional once both models exist. Always keep Vietnamese
            // on device so en/ja/ko/zh/fr ↔ vi works immediately after any pack download.
            val requested = languageCode.trim().lowercase()
            if (requested != TranslateLanguage.VIETNAMESE &&
                !isLanguageDownloaded(TranslateLanguage.VIETNAMESE)
            ) {
                downloadOneMlKitModel(TranslateLanguage.VIETNAMESE, requireWifi)
            }
        }

    private suspend fun downloadOneMlKitModel(languageCode: String, requireWifi: Boolean) {
        val model = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = if (requireWifi) {
            DownloadConditions.Builder().requireWifi().build()
        } else {
            DownloadConditions.Builder().build()
        }
        suspendCancellableCoroutine { cont ->
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
        }
    }

    override suspend fun deleteMlKitLanguage(languageCode: String) =
        withContext(Dispatchers.IO) {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            Tasks.await(modelManager.deleteDownloadedModel(model))
            Unit
        }

    private fun isLanguageDownloaded(languageCode: String): Boolean =
        runCatching {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            Tasks.await(modelManager.isModelDownloaded(model))
        }.getOrDefault(false)

    companion object {
        private const val HF_BASE =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main"
        private const val SHERPA_RELEASE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
        private const val MIN_PIPER_ONNX_BYTES = 512L * 1024L
    }
}
