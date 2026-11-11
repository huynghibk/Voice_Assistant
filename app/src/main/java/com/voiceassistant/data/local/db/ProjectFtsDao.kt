package com.voiceassistant.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectFtsEntity)

    @Query("DELETE FROM project_fts WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)

    @Query("DELETE FROM project_fts")
    suspend fun clear()

    @Query(
        """
        SELECT projectId, title, body FROM project_fts
        WHERE project_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 20): List<ProjectFtsHit>
}

data class ProjectFtsHit(
    val projectId: String,
    val title: String,
    val body: String
)
