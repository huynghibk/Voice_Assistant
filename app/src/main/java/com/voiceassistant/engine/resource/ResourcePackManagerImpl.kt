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
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
        Triple(TranslateLanguage.ENGLISH, TranslateLanguage.VIETNAMESE, "EN ↔ VI"),
        Triple(TranslateLanguage.JAPANESE, TranslateLanguage.VIETNAMESE, "JA ↔ VI"),
        Triple(TranslateLanguage.KOREAN, TranslateLanguage.VIETNAMESE, "KO ↔ VI"),
        Triple(TranslateLanguage.CHINESE, TranslateLanguage.VIETNAMESE, "ZH ↔ VI"),
        Triple(TranslateLanguage.FRENCH, TranslateLanguage.VIETNAMESE, "FR ↔ VI")
    )

    override suspend fun listPiperPacks(): List<PiperVoicePack> = withContext(Dispatchers.IO) {
        voiceRepository.seedPresetsIfEmpty()
        val presets = listOf(
            Triple("preset_vi_demo", "Tiếng Việt", "models/piper/vi_VN-demo-low.onnx"),
            Triple("preset_en_amy", "English · Amy", "models/piper/en_US-amy-low.onnx"),
            Triple("preset_en_ryan", "English · Ryan", "models/piper/en_US-ryan-low.onnx")
        )
        presets.map { (id, name, path) ->
            PiperVoicePack(
                id = id,
                displayName = name,
                modelAssetPath = path,
                qualityLabel = "Low",
                estimatedSizeMb = 45,
                isReady = isPiperPathReady(path)
            )
        }
    }

    override fun isPiperPathReady(modelAssetPath: String?): Boolean {
        if (modelAssetPath.isNullOrBlank()) return false
        if (!modelAssetPath.endsWith(".onnx", ignoreCase = true)) return false
        val external = File(context.filesDir, modelAssetPath)
        if (external.exists() && external.length() > 1024) return true
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
            val conditions = DownloadConditions.Builder().apply {
                if (requireWifi) requireWifi()
            }.build()
            Tasks.await(modelManager.download(model, conditions))
            Unit
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
}
