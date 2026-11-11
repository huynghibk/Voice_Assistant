package com.voiceassistant.presentation.timeline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.timeline.TimelineStore
import com.voiceassistant.domain.model.TimelineProject
import com.voiceassistant.engine.audio.AudioEditorEngine
import com.voiceassistant.engine.audio.AudioExportEngine
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.engine.share.ShareEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class TimelineUiState(
    val selectedClipId: String? = null,
    val status: String = "",
    val isRendering: Boolean = false,
    val lastOutput: String? = null,
    val exportFormat: ExportFormat = ExportFormat.M4A,
    val exportQuality: ExportQuality = ExportQuality.STUDIO
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timelineStore: TimelineStore,
    private val audioEditorEngine: AudioEditorEngine,
    private val audioExportEngine: AudioExportEngine,
    private val shareEngine: ShareEngine
) : ViewModel() {

    val project: StateFlow<TimelineProject> = timelineStore.project
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), timelineStore.project.value)

    private val _ui = MutableStateFlow(TimelineUiState())
    val ui: StateFlow<TimelineUiState> = _ui.asStateFlow()

    fun selectClip(id: String?) = _ui.update { it.copy(selectedClipId = id) }

    fun setFormat(format: ExportFormat) = _ui.update { it.copy(exportFormat = format) }
    fun setQuality(quality: ExportQuality) = _ui.update { it.copy(exportQuality = quality) }

    fun importAudio(uri: Uri, trackIndex: Int = 0) {
        viewModelScope.launch {
            try {
                val dest = File(context.filesDir, "timeline/clip_${System.currentTimeMillis()}.wav")
                dest.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: error("Không đọc được audio")
                timelineStore.addClip(dest.absolutePath, trackIndex = trackIndex, durationHintMs = 5_000L)
                _ui.update { it.copy(status = "Đã thêm clip") }
            } catch (e: Exception) {
                _ui.update { it.copy(status = e.message ?: "Import thất bại") }
            }
        }
    }

    fun addGeneratedWav(path: String, trackIndex: Int = 0) {
        timelineStore.addClip(path, trackIndex = trackIndex)
        _ui.update { it.copy(status = "Đã thêm TTS clip vào timeline") }
    }

    fun updateSelected(
        volume: Float? = null,
        fadeInMs: Long? = null,
        fadeOutMs: Long? = null,
        speed: Float? = null,
        pitch: Float? = null,
        startOffsetMs: Long? = null
    ) {
        val id = _ui.value.selectedClipId ?: return
        val clip = project.value.clips.firstOrNull { it.id == id } ?: return
        timelineStore.updateClip(
            clip.copy(
                volume = volume ?: clip.volume,
                fadeInMs = fadeInMs ?: clip.fadeInMs,
                fadeOutMs = fadeOutMs ?: clip.fadeOutMs,
                speed = speed ?: clip.speed,
                pitch = pitch ?: clip.pitch,
                startOffsetInTimelineMs = startOffsetMs ?: clip.startOffsetInTimelineMs
            )
        )
    }

    fun deleteSelected() {
        val id = _ui.value.selectedClipId ?: return
        timelineStore.removeClip(id)
        _ui.update { it.copy(selectedClipId = null, status = "Đã xóa clip") }
    }

    fun splitAtPlayhead(atMs: Long) {
        val id = _ui.value.selectedClipId ?: return
        timelineStore.splitClip(id, atMs)
        _ui.update { it.copy(status = "Đã tách clip tại ${atMs}ms") }
    }

    fun render() {
        val proj = project.value
        if (proj.clips.isEmpty()) {
            _ui.update { it.copy(status = "Timeline trống") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isRendering = true, status = "Đang render filtergraph…") }
            try {
                val mixed = File(context.cacheDir, "timeline_mix_${System.currentTimeMillis()}.wav")
                audioEditorEngine.renderTimeline(proj, mixed)
                val exported = audioExportEngine.export(
                    inputWav = mixed,
                    format = _ui.value.exportFormat,
                    quality = _ui.value.exportQuality
                )
                _ui.update {
                    it.copy(
                        isRendering = false,
                        lastOutput = exported.absolutePath,
                        status = "Render xong: ${exported.name}"
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(isRendering = false, status = e.message ?: "Render thất bại")
                }
            }
        }
    }

    fun clear() {
        timelineStore.clear()
        _ui.update { it.copy(selectedClipId = null, status = "Đã xóa timeline") }
    }

    fun shareLastOutput() {
        val path = _ui.value.lastOutput ?: run {
            _ui.update { it.copy(status = "Render trước khi chia sẻ.") }
            return
        }
        runCatching {
            shareEngine.shareAudio(File(path))
            _ui.update { it.copy(status = "Đã mở chia sẻ (FileProvider).") }
        }.onFailure { e ->
            _ui.update { it.copy(status = e.message ?: "Chia sẻ thất bại") }
        }
    }
}
