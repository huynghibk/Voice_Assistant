package com.voiceassistant.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.prefs.OnboardingPreferences
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.engine.audio.AudioExportEngine
import com.voiceassistant.engine.audio.AudioRoutingManager
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.engine.audio.PcmStreamPlayer
import com.voiceassistant.engine.playback.TtsPlaybackStarter
import com.voiceassistant.engine.share.ShareEngine
import com.voiceassistant.engine.stt.SpeechEvent
import com.voiceassistant.engine.stt.SpeechToTextEngine
import com.voiceassistant.engine.tts.AndroidSystemTtsSynthesizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val text: String = "",
    val voices: List<VoiceModel> = emptyList(),
    val selectedVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isListening: Boolean = false,
    val progressLabel: String = "",
    val statusMessage: String = "",
    val lastExportPath: String? = null,
    val exportFormat: ExportFormat = ExportFormat.WAV,
    val exportQuality: ExportQuality = ExportQuality.COMPACT,
    val routeLabel: String = "",
    val bufferMultiplier: Int = 2,
    val showSystemTtsDialog: Boolean = false,
    val showOnboarding: Boolean = false,
    val onboardingPiperReady: Boolean = false,
    val onboardingMlKitReady: Boolean = false,
    val onboardingDownloading: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val karaokeSentences: List<String> = emptyList(),
    val karaokeIndex: Int = -1
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val voiceRepository: VoiceRepository,
    private val playbackController: PlaybackController,
    private val audioExportEngine: AudioExportEngine,
    private val shareEngine: ShareEngine,
    private val audioRoutingManager: AudioRoutingManager,
    private val pcmStreamPlayer: PcmStreamPlayer,
    private val androidSystemTtsSynthesizer: AndroidSystemTtsSynthesizer,
    private val speechToTextEngine: SpeechToTextEngine,
    private val ttsResourceGuard: TtsResourceGuard,
    private val resourcePackManager: ResourcePackManager,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var lastPcm: ShortArray = shortArrayOf()
    private var lastSampleRate: Int = 22_050
    private var lastExportFile: File? = null
    private var textBeforeListen: String = ""
    private var lastSeenDefaultVoiceId: String? = null

    val speedPresets: List<Float> = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

    init {
        viewModelScope.launch {
            runCatching { androidSystemTtsSynthesizer.warmUp() }
        }
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.observeVoices().collect { voices ->
                val current = _uiState.value.selectedVoiceId
                val defaultId = voices.firstOrNull { it.isDefault }?.id
                val defaultChanged =
                    defaultId != null &&
                        lastSeenDefaultVoiceId != null &&
                        defaultId != lastSeenDefaultVoiceId
                if (defaultId != null) lastSeenDefaultVoiceId = defaultId
                // After Piper download setDefault(...), switch to that voice for ONNX playback.
                val selected = when {
                    defaultChanged -> defaultId
                    current != null && voices.any { it.id == current } -> current
                    else -> defaultId ?: voices.firstOrNull()?.id
                }
                _uiState.update { it.copy(voices = voices, selectedVoiceId = selected) }
            }
        }
        viewModelScope.launch {
            audioRoutingManager.route.collect { route ->
                _uiState.update {
                    it.copy(
                        routeLabel = route.deviceName,
                        bufferMultiplier = route.bufferMultiplier
                    )
                }
                runCatching { pcmStreamPlayer.invalidateRoute() }
            }
        }
        viewModelScope.launch {
            audioRoutingManager.becomingNoisy.collect {
                stop()
                _uiState.update { it.copy(statusMessage = "Đã dừng — tai nghe bị ngắt kết nối.") }
            }
        }
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                if (playback.isPlaying) {
                    lastPcm = playbackController.getCollectedPcm()
                    lastSampleRate = playbackController.getSampleRate()
                }
                val idx = playback.currentSentenceIndex
                _uiState.update {
                    it.copy(
                        isPlaying = playback.isPlaying,
                        isPaused = playback.isPaused,
                        playbackSpeed = playback.speed,
                        karaokeSentences = playback.sentences,
                        karaokeIndex = idx,
                        progressLabel = if (playback.isPlaying && idx >= 0) {
                            "Câu ${idx + 1} / ${playback.sentenceCount}"
                        } else {
                            ""
                        },
                        statusMessage = playback.statusMessage.ifBlank { it.statusMessage }
                    )
                }
            }
        }
        viewModelScope.launch { maybeShowOnboarding() }
    }

    private suspend fun maybeShowOnboarding() {
        val done = onboardingPreferences.setupDone.first()
        if (done) return
        val piperReady = resourcePackManager.anyPiperReady()
        val mlKitReady = resourcePackManager.isMlKitPairReady("en", "vi")
        if (!piperReady || !mlKitReady) {
            _uiState.update {
                it.copy(
                    showOnboarding = true,
                    onboardingPiperReady = piperReady,
                    onboardingMlKitReady = mlKitReady
                )
            }
        } else {
            onboardingPreferences.setSetupDone(true)
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            onboardingPreferences.setSetupDone(true)
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    fun downloadStarterMlKit() {
        viewModelScope.launch {
            _uiState.update { it.copy(onboardingDownloading = true) }
            runCatching {
                resourcePackManager.downloadMlKitLanguage("en", requireWifi = false)
                resourcePackManager.downloadMlKitLanguage("vi", requireWifi = false)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        onboardingMlKitReady = true,
                        statusMessage = "Đã tải gói dịch EN–VI."
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        statusMessage = e.message ?: "Tải gói dịch thất bại. Vui lòng kiểm tra kết nối mạng."
                    )
                }
            }
        }
    }

    fun dismissSystemTtsDialog(dontShowAgain: Boolean = false) {
        _uiState.update { it.copy(showSystemTtsDialog = false) }
        if (dontShowAgain) {
            viewModelScope.launch {
                onboardingPreferences.setSuppressSystemTtsDialog(true)
            }
        }
    }

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun applyIncomingText(value: String) {
        if (value.isBlank()) return
        _uiState.update {
            it.copy(
                text = value,
                statusMessage = "Đã nhận văn bản — sẵn sàng Phát."
            )
        }
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

    fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (_uiState.value.isPlaying) stop()
        if (!speechToTextEngine.isAvailable()) {
            _uiState.update {
                it.copy(statusMessage = "Thiết bị không hỗ trợ nhận dạng giọng nói.")
            }
            return
        }
        listenJob?.cancel()
        textBeforeListen = _uiState.value.text
        _uiState.update {
            it.copy(isListening = true, statusMessage = "Đang nghe… hãy nói.")
        }
        listenJob = viewModelScope.launch {
            speechToTextEngine.listen(Locale.getDefault()).collect { event ->
                when (event) {
                    SpeechEvent.Ready,
                    SpeechEvent.BeginningOfSpeech -> {
                        _uiState.update { it.copy(statusMessage = "Đang nghe…") }
                    }
                    is SpeechEvent.Partial -> {
                        val merged = mergeTranscript(textBeforeListen, event.text)
                        _uiState.update {
                            it.copy(text = merged, statusMessage = "Đang nghe…")
                        }
                    }
                    is SpeechEvent.Final -> {
                        val merged = mergeTranscript(textBeforeListen, event.text)
                        _uiState.update {
                            it.copy(
                                text = merged,
                                isListening = false,
                                statusMessage = "Đã chuyển giọng nói thành văn bản."
                            )
                        }
                    }
                    is SpeechEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                statusMessage = event.message
                            )
                        }
                    }
                    SpeechEvent.End -> {
                        _uiState.update { state ->
                            if (state.isListening) {
                                state.copy(
                                    isListening = false,
                                    statusMessage = if (state.text != textBeforeListen) {
                                        "Đã chuyển giọng nói thành văn bản."
                                    } else {
                                        state.statusMessage.ifBlank { "Đã dừng ghi âm." }
                                    }
                                )
                            } else state
                        }
                    }
                }
            }
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _uiState.update {
            it.copy(
                isListening = false,
                statusMessage = if (it.text != textBeforeListen) {
                    "Đã chuyển giọng nói thành văn bản."
                } else {
                    "Đã dừng ghi âm."
                }
            )
        }
    }

    private fun mergeTranscript(base: String, spoken: String): String {
        val spokenTrim = spoken.trim()
        if (spokenTrim.isEmpty()) return base
        val baseTrim = base.trimEnd()
        return if (baseTrim.isEmpty()) spokenTrim
        else "$baseTrim $spokenTrim"
    }

    fun play() {
        val state = _uiState.value
        val text = state.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Nhập hoặc ghi âm văn bản để đọc.") }
            return
        }
        val voice = state.voices.firstOrNull { it.id == state.selectedVoiceId } ?: return
        if (state.isListening) stopListening()
        if (state.isPlaying) {
            stop()
        }

        viewModelScope.launch {
            val advice = ttsResourceGuard.advisePlay(voice)
            val suppress = onboardingPreferences.suppressSystemTtsDialog.first()
            _uiState.update {
                it.copy(
                    statusMessage = advice.message ?: "Đang tổng hợp…",
                    showSystemTtsDialog = if (suppress) false else advice.suggestOpenModelManager
                )
            }
            // Clear STT/SCO earpiece routing before TTS media playback.
            audioRoutingManager.ensureMediaMode()
            playbackController.setSpeed(state.playbackSpeed)
            playbackController.start(text, voice, title = voice.name)
            TtsPlaybackStarter.start(appContext)
        }
    }

    fun stop() {
        playbackController.stop()
        lastPcm = playbackController.getCollectedPcm()
        lastSampleRate = playbackController.getSampleRate()
        TtsPlaybackStarter.stop(appContext)
    }

    fun setSpeed(speed: Float) {
        playbackController.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun skipNext() = playbackController.skipToNextSentence()
    fun skipPrevious() = playbackController.skipToPreviousSentence()

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
        listenJob?.cancel()
        // Do not stop playback — background service owns it
        super.onCleared()
    }
}
