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

    // HuggingFace rhasspy/piper-voices — .onnx + .onnx.json pairs.
    private val piperCatalog = listOf(
        PiperVoicePack(
            id = "preset_vi_demo",
            displayName = "Tiếng Việt",
            modelAssetPath = "models/piper/vi_VN-demo-low.onnx",
            qualityLabel = "Low",
            estimatedSizeMb = 63,
            modelDownloadUrl =
                "$HF_BASE/vi/vi_VN/25hours_single/low/vi_VN-25hours_single-low.onnx",
            configDownloadUrl =
                "$HF_BASE/vi/vi_VN/25hours_single/low/vi_VN-25hours_single-low.onnx.json"
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
        val config = File(context.filesDir, "$modelAssetPath.json")
        if (external.exists() && external.length() > 1024 &&
            config.exists() && config.length() > 16
        ) {
            return true
        }
        // Also accept assets-bundled models (legacy).
        return runCatching {
            context.assets.openFd(modelAssetPath).use { it.length > 1024 }
        }.getOrElse {
            runCatching {
                context.assets.open(modelAssetPath).use { input ->
                    var total = 0
                    val buf = ByteArray(8 * 1024)
                    while (total <= 1024) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                    }
                    total > 1024
                }
            }.getOrDefault(false)
        }
    }

    override suspend fun anyPiperReady(): Boolean =
        listPiperPacks().any { it.isReady }

    override suspend fun downloadPiperVoice(packId: String) = withContext(Dispatchers.IO) {
        val pack = piperCatalog.firstOrNull { it.id == packId }
            ?: error("Không tìm thấy gói giọng $packId.")
        val modelUrl = pack.modelDownloadUrl
            ?: error("Gói ${pack.displayName} chưa hỗ trợ tải về.")
        val configUrl = pack.configDownloadUrl
            ?: error("Gói ${pack.displayName} thiếu URL file cấu hình.")

        val modelFile = File(context.filesDir, pack.modelAssetPath)
        val configFile = File(context.filesDir, "${pack.modelAssetPath}.json")
        modelFile.parentFile?.mkdirs()

        val modelTmp = File(modelFile.parentFile, "${modelFile.name}.part")
        val configTmp = File(configFile.parentFile, "${configFile.name}.part")
        try {
            downloadToFile(modelUrl, modelTmp)
            downloadToFile(configUrl, configTmp)
            if (modelTmp.length() < 1024L) {
                error("File model Piper tải về bị lỗi (quá nhỏ).")
            }
            if (configTmp.length() < 16L) {
                error("File cấu hình Piper tải về bị lỗi.")
            }
            if (modelFile.exists()) modelFile.delete()
            if (configFile.exists()) configFile.delete()
            if (!modelTmp.renameTo(modelFile)) {
                modelTmp.copyTo(modelFile, overwrite = true)
                modelTmp.delete()
            }
            if (!configTmp.renameTo(configFile)) {
                configTmp.copyTo(configFile, overwrite = true)
                configTmp.delete()
            }
            // Prefer this voice so playback switches to Piper ONNX automatically.
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.setDefault(pack.id)
        } catch (e: Exception) {
            modelTmp.delete()
            configTmp.delete()
            throw e
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
    }
}
