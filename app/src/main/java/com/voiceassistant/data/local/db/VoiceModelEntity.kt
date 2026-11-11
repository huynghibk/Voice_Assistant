package com.voiceassistant.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_models")
data class VoiceModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tagsCsv: String = "",
    val embeddingPath: String? = null,
    val sampleAudioPath: String? = null,
    val modelAssetPath: String? = null,
    val isDefault: Boolean = false,
    val isPreset: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
