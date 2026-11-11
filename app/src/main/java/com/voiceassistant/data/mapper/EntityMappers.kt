package com.voiceassistant.data.mapper

import com.voiceassistant.data.local.db.TextProjectEntity
import com.voiceassistant.data.local.db.VoiceModelEntity
import com.voiceassistant.domain.model.ProjectStatus
import com.voiceassistant.domain.model.TextProject
import com.voiceassistant.domain.model.VoiceModel

fun VoiceModelEntity.toDomain(): VoiceModel = VoiceModel(
    id = id,
    name = name,
    tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    embeddingPath = embeddingPath,
    sampleAudioPath = sampleAudioPath,
    modelAssetPath = modelAssetPath,
    isDefault = isDefault,
    isPreset = isPreset,
    createdAt = createdAt
)

fun VoiceModel.toEntity(): VoiceModelEntity = VoiceModelEntity(
    id = id,
    name = name,
    tagsCsv = tags.joinToString(","),
    embeddingPath = embeddingPath,
    sampleAudioPath = sampleAudioPath,
    modelAssetPath = modelAssetPath,
    isDefault = isDefault,
    isPreset = isPreset,
    createdAt = createdAt
)

fun TextProjectEntity.toDomain(): TextProject = TextProject(
    id = id,
    title = title,
    content = content,
    voiceId = voiceId,
    customPausesJson = customPausesJson,
    status = runCatching { ProjectStatus.valueOf(status) }.getOrDefault(ProjectStatus.DRAFT),
    updatedAt = updatedAt
)

fun TextProject.toEntity(): TextProjectEntity = TextProjectEntity(
    id = id,
    title = title,
    content = content,
    voiceId = voiceId,
    customPausesJson = customPausesJson,
    status = status.name,
    updatedAt = updatedAt
)
