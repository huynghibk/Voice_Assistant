package com.voiceassistant.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TextProjectDao {
    @Query("SELECT * FROM text_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TextProjectEntity>>

    @Query("SELECT * FROM text_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TextProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TextProjectEntity)

    @Query("DELETE FROM text_projects WHERE id = :id")
    suspend fun delete(id: String)
}
