package com.voiceassistant.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.usecase.SynthesizeSpeechUseCase
import com.voiceassistant.engine.audio.AudioExportEngine
import com.voiceassistant.engine.audio.AudioRoutingManager
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.engine.audio.PcmStreamPlayer
import com.voiceassistant.engine.share.ShareEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val text: String = "",
    val voices: List<VoiceModel> = emptyList(),
    val selectedVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val progressLabel: String = "",
    val statusMessage: String = "",
    val lastExportPath: String? = null,
    val exportFormat: ExportFormat = ExportFormat.WAV,
    val exportQuality: ExportQuality = ExportQuality.COMPACT,
    val routeLabel: String = "Loa máy",
    val bufferMultiplier: Int = 2
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val synthesizeSpeechUseCase: SynthesizeSpeechUseCase,
    private val audioExportEngine: AudioExportEngine,
    private val shareEngine: ShareEngine,
    private val audioRoutingManager: AudioRoutingManager,
    private val pcmStreamPlayer: PcmStreamPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var lastPcm: ShortArray = shortArrayOf()
    private var lastSampleRate: Int = 22_050
    private var lastExportFile: File? = null

    init {
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.observeVoices().collect { voices ->
                val selected = _uiState.value.selectedVoiceId
                    ?: voices.firstOrNull { it.isDefault }?.id
                    ?: voices.firstOrNull()?.id
                _uiState.update { it.copy(voices = voices, selectedVoiceId = selected) }
            }
        }
        viewModelScope.launch {
            audioRoutingManager.route.collect { route ->
                _uiState.update {
                    it.copy(
                        routeLabel = "${route.deviceName} · buf×${route.bufferMultiplier}",
                        bufferMultiplier = route.bufferMultiplier
                    )
                }
                // Rebuild AudioTrack buffer when Bluetooth/Cast connects mid-session
                runCatching { pcmStreamPlayer.invalidateRoute() }
            }
        }
        viewModelScope.launch {
            audioRoutingManager.becomingNoisy.collect {
                stop()
                _uiState.update { it.copy(statusMessage = "Đã dừng — tai nghe bị ngắt kết nối.") }
            }
        }
    }

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun onVoiceSelected(id: String) {
        _uiState.update { it.copy(selectedVoiceId = id) }
    }

    fun onExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun onExportQuality(quality: ExportQuality) {
        _uiState.update { it.copy(exportQuality = quality) }
    }

    fun play() {
        val state = _uiState.value
        val text = state.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Nhập văn bản để đọc.") }
            return
        }
        val voice = state.voices.firstOrNull { it.id == state.selectedVoiceId } ?: return

        playJob?.cancel()
        playJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isPlaying = true, statusMessage = "Đang tổng hợp…", progressLabel = "")
            }
            try {
                synthesizeSpeechUseCase.execute(text, voice).collect { progress ->
                    lastPcm = progress.collectedPcm
                    lastSampleRate = progress.chunk.sampleRateHz
                    val idx = progress.chunk.sentenceIndex + 1
                    val total = progress.chunk.sentenceCount
                    _uiState.update {
                        it.copy(
                            progressLabel = "Câu $idx / $total",
                            statusMessage = progress.chunk.sentence.take(80)
                        )
                    }
                }
                _uiState.update {
                    it.copy(isPlaying = false, statusMessage = "Hoàn tất.", progressLabel = "")
                }
            } catch (e: Exception) {
                synthesizeSpeechUseCase.stop()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        statusMessage = e.message ?: "Đã dừng.",
                        progressLabel = ""
                    )
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        viewModelScope.launch {
            synthesizeSpeechUseCase.stop()
            _uiState.update {
                it.copy(isPlaying = false, statusMessage = "Đã dừng.", progressLabel = "")
            }
        }
    }

    fun exportAudio() {
        if (lastPcm.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Chưa có audio để xuất. Hãy bấm Phát trước.") }
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val file = audioExportEngine.exportPcm(
                    lastPcm,
                    lastSampleRate,
                    state.exportFormat,
                    state.exportQuality
                )
                lastExportFile = file
                _uiState.update {
                    it.copy(
                        lastExportPath = file.absolutePath,
                        statusMessage = "Đã xuất ${state.exportFormat}: ${file.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = e.message ?: "Export thất bại") }
            }
        }
    }

    fun shareLastExport() {
        val file = lastExportFile
        if (file == null || !file.exists()) {
            if (lastPcm.isEmpty()) {
                _uiState.update { it.copy(statusMessage = "Xuất audio trước khi chia sẻ.") }
                return
            }
            viewModelScope.launch {
                try {
                    val exported = audioExportEngine.exportPcm(
                        lastPcm,
                        lastSampleRate,
                        _uiState.value.exportFormat,
                        _uiState.value.exportQuality
                    )
                    lastExportFile = exported
                    shareEngine.shareAudio(exported)
                    _uiState.update {
                        it.copy(
                            lastExportPath = exported.absolutePath,
                            statusMessage = "Đã mở chia sẻ: ${exported.name}"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(statusMessage = e.message ?: "Chia sẻ thất bại") }
                }
            }
            return
        }
        runCatching {
            shareEngine.shareAudio(file)
            _uiState.update { it.copy(statusMessage = "Đã mở chia sẻ hệ thống.") }
        }.onFailure { e ->
            _uiState.update { it.copy(statusMessage = e.message ?: "Chia sẻ thất bại") }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
