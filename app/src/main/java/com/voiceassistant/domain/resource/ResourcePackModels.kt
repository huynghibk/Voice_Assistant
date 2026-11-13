package com.voiceassistant.domain.resource

// Catalog models for the offline resource center.
data class PiperVoicePack(
    val id: String,
    val displayName: String,
    val modelAssetPath: String,
    val qualityLabel: String = "Low",
    val estimatedSizeMb: Int = 63,
    val isReady: Boolean = false,
    val modelDownloadUrl: String? = null,
    val configDownloadUrl: String? = null,
    // k2-fsa/sherpa-onnx release tar.bz2 (Melo, Piper packs, …)
    val sherpaTarballUrl: String? = null,
    // Extra files next to model.onnx (e.g. tokens.txt for MMS)
    val companionDownloadUrls: Map<String, String> = emptyMap()
) {
    fun hasDownloadSource(): Boolean =
        !modelDownloadUrl.isNullOrBlank() || !sherpaTarballUrl.isNullOrBlank()
}

data class MlKitLanguagePack(
    val languageCode: String,
    val displayName: String,
    val estimatedSizeMb: Int = 30,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false
)

data class MlKitPairPack(
    val sourceCode: String,
    val targetCode: String,
    val displayLabel: String,
    val estimatedSizeMb: Int,
    val isInstalled: Boolean
)

sealed class ResourceActionState {
    data object Idle : ResourceActionState()
    data class Progress(val packId: String, val message: String = "") : ResourceActionState()
    data class Error(val packId: String, val message: String) : ResourceActionState()
}
