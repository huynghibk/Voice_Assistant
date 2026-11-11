package com.voiceassistant.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "text_projects")
data class TextProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val voiceId: String?,
    val customPausesJson: String = "[]",
    val status: String = "DRAFT",
    val updatedAt: Long = System.currentTimeMillis()
)
