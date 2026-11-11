package com.voiceassistant.domain.model

enum class ProjectStatus {
    DRAFT,
    DONE
}

data class TextProject(
    val id: String,
    val title: String,
    val content: String,
    val voiceId: String?,
    val customPausesJson: String = "[]",
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val updatedAt: Long = System.currentTimeMillis()
)
