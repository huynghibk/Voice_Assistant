package com.voiceassistant.domain.model

data class TimelineProject(
    val id: String,
    val title: String,
    val clips: List<AudioClip> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun durationMs(): Long =
        clips.maxOfOrNull { clip ->
            val trimEnd = clip.trimEndMs ?: Long.MAX_VALUE / 4
            val clipDur = (trimEnd - clip.trimStartMs).coerceAtLeast(0L)
            clip.startOffsetInTimelineMs + (clipDur / clip.speed.coerceAtLeast(0.1f)).toLong()
        } ?: 0L
}
