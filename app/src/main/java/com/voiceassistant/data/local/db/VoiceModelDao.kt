package com.voiceassistant.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceModelDao {
    @Query("SELECT * FROM voice_models ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<VoiceModelEntity>>

    @Query("SELECT * FROM voice_models ORDER BY isDefault DESC, name ASC")
    suspend fun listAll(): List<VoiceModelEntity>

    @Query("SELECT * FROM voice_models WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VoiceModelEntity?

    @Query("SELECT * FROM voice_models WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): VoiceModelEntity?

    @Query("SELECT COUNT(*) FROM voice_models")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VoiceModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VoiceModelEntity>)

    @Update
    suspend fun update(entity: VoiceModelEntity)

    @Query("UPDATE voice_models SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE voice_models SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)

    @Query("DELETE FROM voice_models WHERE id = :id")
    suspend fun delete(id: String)
}
