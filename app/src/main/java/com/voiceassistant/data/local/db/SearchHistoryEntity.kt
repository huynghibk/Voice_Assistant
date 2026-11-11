package com.voiceassistant.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val response: String,
    val source: String = "local", // local | web | hybrid
    val timestamp: Long = System.currentTimeMillis()
)
