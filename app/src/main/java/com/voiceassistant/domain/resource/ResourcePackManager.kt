package com.voiceassistant.domain.resource

// Central offline resource hub (Piper download + ML Kit download/delete)
interface ResourcePackManager {
    suspend fun listPiperPacks(): List<PiperVoicePack>
    fun isPiperPathReady(modelAssetPath: String?): Boolean
    suspend fun anyPiperReady(): Boolean
    suspend fun downloadPiperVoice(packId: String)

    suspend fun listMlKitLanguages(): List<MlKitLanguagePack>
    suspend fun listRecommendedPairs(): List<MlKitPairPack>
    suspend fun isMlKitPairReady(sourceCode: String, targetCode: String): Boolean
    suspend fun downloadMlKitLanguage(languageCode: String, requireWifi: Boolean = false)
    suspend fun deleteMlKitLanguage(languageCode: String)
}
