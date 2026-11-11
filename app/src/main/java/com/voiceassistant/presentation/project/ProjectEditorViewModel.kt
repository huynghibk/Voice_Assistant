package com.voiceassistant.presentation.project

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.importing.DocumentImporter
import com.voiceassistant.data.timeline.TimelineStore
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.model.ProjectStatus
import com.voiceassistant.domain.model.TextProject
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.ProjectRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.usecase.SynthesizeSpeechUseCase
import com.voiceassistant.engine.audio.AudioExportEngine
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.engine.share.ShareEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProjectEditorUiState(
    val projectId: String = "",
    val title: String = "Dự án mới",
    val content: String = "",
    val voices: List<VoiceModel> = emptyList(),
    val selectedVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val statusMessage: String = "",
    val saved: Boolean = false,
    val sourceLang: String = "vi",
    val targetLang: String = "en",
    val languages: List<OfflineTranslationService.Lang> = emptyList(),
    val exportFormat: ExportFormat = ExportFormat.WAV,
    val exportQuality: ExportQuality = ExportQuality.STUDIO,
    val lastExportPath: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val voiceRepository: VoiceRepository,
    private val synthesizeSpeechUseCase: SynthesizeSpeechUseCase,
    private val documentImporter: DocumentImporter,
    private val translationService: OfflineTranslationService,
    private val audioExportEngine: AudioExportEngine,
    private val timelineStore: TimelineStore,
    private val shareEngine: ShareEngine
) : ViewModel() {

    private val routeId: String = savedStateHandle["projectId"] ?: "new"

    private val _uiState = MutableStateFlow(
        ProjectEditorUiState(languages = translationService.supported)
    )
    val uiState: StateFlow<ProjectEditorUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var lastPcm: ShortArray = shortArrayOf()
    private var lastSampleRate: Int = 22_050

    init {
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            val voices = voiceRepository.observeVoices()
            val existing = if (routeId != "new") projectRepository.getProject(routeId) else null
            val id = existing?.id ?: UUID.randomUUID().toString()
            _uiState.update {
                it.copy(
                    projectId = id,
                    title = existing?.title ?: "Dự án mới",
                    content = existing?.content.orEmpty(),
                    selectedVoiceId = existing?.voiceId
                )
            }
            voices.collect { list ->
                val selected = _uiState.value.selectedVoiceId
                    ?: list.firstOrNull { it.isDefault }?.id
                    ?: list.firstOrNull()?.id
                _uiState.update { it.copy(voices = list, selectedVoiceId = selected) }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, saved = false) }
    fun onContentChange(value: String) = _uiState.update { it.copy(content = value, saved = false) }
    fun onVoiceSelected(id: String) = _uiState.update { it.copy(selectedVoiceId = id, saved = false) }
    fun onSourceLang(code: String) = _uiState.update { it.copy(sourceLang = code) }
    fun onTargetLang(code: String) = _uiState.update { it.copy(targetLang = code) }
    fun onExportFormat(f: ExportFormat) = _uiState.update { it.copy(exportFormat = f) }
    fun onExportQuality(q: ExportQuality) = _uiState.update { it.copy(exportQuality = q) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            projectRepository.upsert(
                TextProject(
                    id = state.projectId,
                    title = state.title.ifBlank { "Dự án không tên" },
                    content = state.content,
                    voiceId = state.selectedVoiceId,
                    status = ProjectStatus.DRAFT,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(saved = true, statusMessage = "Đã lưu bản nháp.") }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Đang import…") }
            try {
                val text = documentImporter.importText(uri)
                _uiState.update {
                    it.copy(
                        content = text,
                        busy = false,
                        saved = false,
                        statusMessage = "Đã import ${text.length} ký tự"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(busy = false, statusMessage = e.message ?: "Import thất bại")
                }
            }
        }
    }

    fun translate() {
        val state = _uiState.value
        if (state.content.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Nội dung trống.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Đang dịch offline (tải model nếu cần)…") }
            try {
                val translated = translationService.translate(
                    text = state.content,
                    source = state.sourceLang,
                    target = state.targetLang
                )
                _uiState.update {
                    it.copy(
                        content = translated,
                        busy = false,
                        saved = false,
                        statusMessage = "Đã dịch ${state.sourceLang} → ${state.targetLang}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(busy = false, statusMessage = e.message ?: "Dịch thất bại")
                }
            }
        }
    }

    fun play() {
        val state = _uiState.value
        val text = state.content.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Nội dung trống.") }
            return
        }
        val voice = state.voices.firstOrNull { it.id == state.selectedVoiceId } ?: return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlaying = true, statusMessage = "Đang đọc…") }
            try {
                synthesizeSpeechUseCase.execute(text, voice).collect { progress ->
                    lastPcm = progress.collectedPcm
                    lastSampleRate = progress.chunk.sampleRateHz
                    _uiState.update {
                        it.copy(
                            statusMessage = "Câu ${progress.chunk.sentenceIndex + 1}/${progress.chunk.sentenceCount}"
                        )
                    }
                }
                _uiState.update { it.copy(isPlaying = false, statusMessage = "Hoàn tất.") }
            } catch (e: Exception) {
                synthesizeSpeechUseCase.stop()
                _uiState.update {
                    it.copy(isPlaying = false, statusMessage = e.message ?: "Đã dừng.")
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        viewModelScope.launch {
            synthesizeSpeechUseCase.stop()
            _uiState.update { it.copy(isPlaying = false, statusMessage = "Đã dừng.") }
        }
    }

    fun exportAudio() {
        if (lastPcm.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Hãy Phát trước để tạo audio.") }
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Đang xuất ${state.exportFormat}…") }
            try {
                val file = audioExportEngine.exportPcm(
                    pcm16le = lastPcm,
                    sampleRateHz = lastSampleRate,
                    format = state.exportFormat,
                    quality = state.exportQuality
                )
                _uiState.update {
                    it.copy(
                        busy = false,
                        lastExportPath = file.absolutePath,
                        statusMessage = "Đã xuất: ${file.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(busy = false, statusMessage = e.message ?: "Export thất bại")
                }
            }
        }
    }

    fun sendLastAudioToTimeline() {
        viewModelScope.launch {
            if (lastPcm.isEmpty()) {
                _uiState.update { it.copy(statusMessage = "Chưa có audio.") }
                return@launch
            }
            try {
                val wav = audioExportEngine.exportWav(lastPcm, lastSampleRate, "timeline_${System.currentTimeMillis()}.wav")
                timelineStore.addClip(wav.absolutePath, trackIndex = 0)
                _uiState.update { it.copy(statusMessage = "Đã gửi clip vào Timeline") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = e.message ?: "Lỗi timeline") }
            }
        }
    }

    fun shareLastExport() {
        val path = _uiState.value.lastExportPath
        if (path.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = "Xuất file trước khi chia sẻ.") }
            return
        }
        runCatching {
            shareEngine.shareAudio(File(path))
            _uiState.update { it.copy(statusMessage = "Đã mở chia sẻ hệ thống.") }
        }.onFailure { e ->
            _uiState.update { it.copy(statusMessage = e.message ?: "Chia sẻ thất bại") }
        }
    }
}
