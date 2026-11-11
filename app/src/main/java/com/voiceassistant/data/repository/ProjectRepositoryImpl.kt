package com.voiceassistant.data.repository

import com.voiceassistant.data.local.db.ProjectFtsDao
import com.voiceassistant.data.local.db.ProjectFtsEntity
import com.voiceassistant.data.local.db.TextProjectDao
import com.voiceassistant.data.mapper.toDomain
import com.voiceassistant.data.mapper.toEntity
import com.voiceassistant.domain.model.TextProject
import com.voiceassistant.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: TextProjectDao,
    private val ftsDao: ProjectFtsDao
) : ProjectRepository {

    override fun observeProjects(): Flow<List<TextProject>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProject(id: String): TextProject? =
        dao.getById(id)?.toDomain()

    override suspend fun upsert(project: TextProject) {
        dao.upsert(project.toEntity())
        // Keep FTS index in sync without burdening UI queries
        ftsDao.deleteByProjectId(project.id)
        val rowId = (project.id.hashCode() and 0x7fffffff).let { if (it == 0) 1 else it }
        ftsDao.upsert(
            ProjectFtsEntity(
                rowid = rowId,
                projectId = project.id,
                title = project.title,
                body = project.content
            )
        )
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        ftsDao.deleteByProjectId(id)
    }
}
