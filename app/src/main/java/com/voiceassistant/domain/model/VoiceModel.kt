package com.voiceassistant.domain.model

data class VoiceModel(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val embeddingPath: String? = null,
    val sampleAudioPath: String? = null,
    val modelAssetPath: String? = null,
    val isDefault: Boolean = false,
    val isPreset: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
