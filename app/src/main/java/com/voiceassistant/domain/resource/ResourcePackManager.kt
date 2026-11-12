package com.voiceassistant.domain.resource

/**
 * Central offline resource hub.
 * Phase 1: Piper local status + ML Kit download/delete.
 * Later: Piper remote catalog / WorkManager downloads.
 */
interface ResourcePackManager {
    suspend fun listPiperPacks(): List<PiperVoicePack>
    fun isPiperPathReady(modelAssetPath: String?): Boolean
    suspend fun anyPiperReady(): Boolean

    suspend fun listMlKitLanguages(): List<MlKitLanguagePack>
    suspend fun listRecommendedPairs(): List<MlKitPairPack>
    suspend fun isMlKitPairReady(sourceCode: String, targetCode: String): Boolean
    suspend fun downloadMlKitLanguage(languageCode: String, requireWifi: Boolean = true)
    suspend fun deleteMlKitLanguage(languageCode: String)
}
