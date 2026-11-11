package com.voiceassistant.domain.model

/**
 * Timeline clip metadata only — never bake intermediate files on every edit.
 * Render builds one FFmpeg complex filtergraph from the full clip list.
 */
data class AudioClip(
    val id: String,
    val sourceFilePath: String,
    val startOffsetInTimelineMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val trackIndex: Int = 0
)
