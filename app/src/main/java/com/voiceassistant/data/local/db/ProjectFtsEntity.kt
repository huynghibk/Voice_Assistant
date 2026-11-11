package com.voiceassistant.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Independent FTS4 virtual table for project text search —
 * keeps heavy MATCH queries off the core CRUD tables' hot path.
 */
@Fts4
@Entity(tableName = "project_fts")
data class ProjectFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,
    val projectId: String,
    val title: String,
    val body: String
)
