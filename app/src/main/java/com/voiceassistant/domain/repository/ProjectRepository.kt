package com.voiceassistant.domain.repository

import com.voiceassistant.domain.model.TextProject
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<TextProject>>
    suspend fun getProject(id: String): TextProject?
    suspend fun upsert(project: TextProject)
    suspend fun delete(id: String)
}
