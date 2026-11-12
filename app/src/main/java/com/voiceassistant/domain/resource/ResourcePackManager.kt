package com.voiceassistant.domain.resource

// Central offline resource hub (Piper local status + ML Kit download/delete)
interface ResourcePackManager {
    suspend fun listPiperPacks(): List<PiperVoicePack>
    fun isPiperPathReady(modelAssetPath: String?): Boolean
    suspend fun anyPiperReady(): Boolean

    suspend fun listMlKitLanguages(): List<MlKitLanguagePack>
    suspend fun listRecommendedPairs(): List<MlKitPairPack>
    suspend fun isMlKitPairReady(sourceCode: String, targetCode: String): Boolean
    suspend fun downloadMlKitLanguage(languageCode: String, requireWifi: Boolean = false)
    suspend fun deleteMlKitLanguage(languageCode: String)
}
