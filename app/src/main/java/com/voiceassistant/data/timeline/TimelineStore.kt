package com.voiceassistant.data.timeline

import com.voiceassistant.domain.model.AudioClip
import com.voiceassistant.domain.model.TimelineProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineStore @Inject constructor() {

    private val _project = MutableStateFlow(
        TimelineProject(id = "default", title = "Studio Timeline")
    )
    val project: StateFlow<TimelineProject> = _project.asStateFlow()

    fun setTitle(title: String) {
        _project.update { it.copy(title = title, updatedAt = System.currentTimeMillis()) }
    }

    fun addClip(
        sourceFilePath: String,
        trackIndex: Int = 0,
        startOffsetInTimelineMs: Long? = null,
        durationHintMs: Long = 3_000L
    ): AudioClip {
        val current = _project.value
        val offset = startOffsetInTimelineMs ?: current.durationMs()
        val clip = AudioClip(
            id = UUID.randomUUID().toString(),
            sourceFilePath = sourceFilePath,
            startOffsetInTimelineMs = offset,
            trimStartMs = 0L,
            trimEndMs = durationHintMs,
            volume = 1f,
            trackIndex = trackIndex
        )
        _project.update {
            it.copy(clips = it.clips + clip, updatedAt = System.currentTimeMillis())
        }
        return clip
    }

    fun updateClip(clip: AudioClip) {
        _project.update { proj ->
            proj.copy(
                clips = proj.clips.map { if (it.id == clip.id) clip else it },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun removeClip(id: String) {
        _project.update {
            it.copy(clips = it.clips.filterNot { c -> c.id == id }, updatedAt = System.currentTimeMillis())
        }
    }

    fun splitClip(id: String, atTimelineMs: Long) {
        val proj = _project.value
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
        val localPos = atTimelineMs - clip.startOffsetInTimelineMs
        if (localPos <= 0) return
        val trimEnd = clip.trimEndMs ?: return
        val absoluteTrim = clip.trimStartMs + localPos
        if (absoluteTrim >= trimEnd) return

        val left = clip.copy(trimEndMs = absoluteTrim)
        val right = clip.copy(
            id = UUID.randomUUID().toString(),
            startOffsetInTimelineMs = atTimelineMs,
            trimStartMs = absoluteTrim
        )
        _project.update {
            it.copy(
                clips = it.clips.flatMap { c -> if (c.id == id) listOf(left, right) else listOf(c) },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun clear() {
        _project.update { it.copy(clips = emptyList(), updatedAt = System.currentTimeMillis()) }
    }
}
