package com.voiceassistant.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VoiceModelEntity::class,
        TextProjectEntity::class,
        SearchHistoryEntity::class,
        ProjectFtsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceModelDao(): VoiceModelDao
    abstract fun textProjectDao(): TextProjectDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun projectFtsDao(): ProjectFtsDao
}
