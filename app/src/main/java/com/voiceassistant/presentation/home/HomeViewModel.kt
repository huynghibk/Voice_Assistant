package com.voiceassistant.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.R
import com.voiceassistant.data.prefs.OnboardingPreferences
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.translation.LanguageCodes
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.domain.voice.ClonedVoiceSupport
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

enum class HomeReadMode {
    ORIGINAL,
    TRANSLATE_AND_READ
}

data class HomeUiState(
    val text: String = "",
    val voices: List<VoiceModel> = emptyList(),
    /** voiceId → true when Piper .onnx exists under filesDir/models/piper/ */
    val piperReadyByVoiceId: Map<String, Boolean> = emptyMap(),
    val selectedVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isListening: Boolean = false,
    /** UI should launch the system speech screen. */
    val pendingSpeechIntent: Boolean = false,
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
    val onboardingDownloadingMessage: String = "",
    val playbackSpeed: Float = 1.0f,
    val karaokeSentences: List<String> = emptyList(),
    val karaokeIndex: Int = -1,
    val readMode: HomeReadMode = HomeReadMode.ORIGINAL,
    val translateLanguages: List<OfflineTranslationService.Lang> = emptyList(),
    val selectedTargetLang: String = "vi",
    val translatedText: String = "",
    val isTranslating: Boolean = false
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
    private val onboardingPreferences: OnboardingPreferences,
    private val translationRepository: TranslationRepository,
    private val languageIdentifier: LanguageIdentifier,
    private val offlineTranslationService: OfflineTranslationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(translateLanguages = offlineTranslationService.supported)
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastPcm: ShortArray = shortArrayOf()
    private var lastSampleRate: Int = 22_050
    private var lastExportFile: File? = null
    private var textBeforeListen: String = ""
    private var speechLocale: Locale = Locale.getDefault()
    private var listenJob: Job? = null
    private var lastSeenDefaultVoiceId: String? = null

    val speedPresets: List<Float> = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

    init {
        viewModelScope.launch {
            runCatching { androidSystemTtsSynthesizer.warmUp() }
        }
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.observeVoices().collect { voices ->
                val mmsId = "preset_vi_demo"
                val piperViId = "preset_vi_piper_25h"
                val defaultVoice = voices.firstOrNull { it.isDefault }
                if (defaultVoice?.id == mmsId &&
                    !resourcePackManager.isPiperPathReady(defaultVoice.modelAssetPath) &&
                    voices.any { it.id == piperViId } &&
                    resourcePackManager.isPiperPathReady(
                        voices.first { it.id == piperViId }.modelAssetPath
                    )
                ) {
                    voiceRepository.setDefault(piperViId)
                    return@collect
                }
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
                val piperReady = voices.associate { v ->
                    v.id to ClonedVoiceSupport.basePiperReady(v, voices, resourcePackManager)
                }
                _uiState.update {
                    it.copy(
                        voices = voices,
                        selectedVoiceId = selected,
                        piperReadyByVoiceId = piperReady
                    )
                }
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
            var lastPlaybackSentence = -1
            playbackController.state.collect { playback ->
                val idx = playback.currentSentenceIndex
                if (playback.isPlaying && idx >= 0 && idx != lastPlaybackSentence) {
                    lastPlaybackSentence = idx
                    lastPcm = playbackController.getCollectedPcm()
                    lastSampleRate = playbackController.getSampleRate()
                } else if (!playback.isPlaying && lastPlaybackSentence >= 0) {
                    lastPcm = playbackController.getCollectedPcm()
                    lastSampleRate = playbackController.getSampleRate()
                    lastPlaybackSentence = -1
                }
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
            _uiState.update {
                it.copy(
                    onboardingDownloading = true,
                    onboardingDownloadingMessage = "Đang tải gói dịch EN–VI…"
                )
            }
            runCatching {
                resourcePackManager.downloadMlKitLanguage("en", requireWifi = false)
                resourcePackManager.downloadMlKitLanguage("vi", requireWifi = false)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        onboardingDownloadingMessage = "",
                        onboardingMlKitReady = true,
                        statusMessage = "Đã tải gói dịch EN–VI."
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        onboardingDownloadingMessage = "",
                        statusMessage = e.message ?: "Tải gói dịch thất bại. Vui lòng kiểm tra kết nối mạng."
                    )
                }
            }
        }
    }

    fun downloadStarterPiper() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    onboardingDownloading = true,
                    onboardingDownloadingMessage = "Đang tải gói giọng đọc Piper…"
                )
            }
            runCatching {
                resourcePackManager.downloadPiperVoice("preset_vi_demo")
                resourcePackManager.downloadPiperVoice("preset_en_amy")
            }.onSuccess {
                val ready = resourcePackManager.anyPiperReady()
                val catalog = _uiState.value.voices
                val updatedPiperMap = catalog.associate { v ->
                    v.id to ClonedVoiceSupport.basePiperReady(v, catalog, resourcePackManager)
                }
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        onboardingDownloadingMessage = "",
                        onboardingPiperReady = ready,
                        piperReadyByVoiceId = updatedPiperMap,
                        statusMessage = "Đã tải gói giọng đọc Piper."
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        onboardingDownloading = false,
                        onboardingDownloadingMessage = "",
                        statusMessage = e.message ?: "Tải gói giọng đọc Piper thất bại. Vui lòng kiểm tra kết nối mạng."
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

    fun onReadModeSelected(mode: HomeReadMode) {
        _uiState.update {
            it.copy(
                readMode = mode,
                translatedText = if (mode == HomeReadMode.ORIGINAL) "" else it.translatedText
            )
        }
    }

    fun onTargetLang(id: String) = _uiState.update { it.copy(selectedTargetLang = id) }

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
        listenJob?.cancel()
        listenJob = null
        val state = _uiState.value
        val voice = state.voices.firstOrNull { it.id == state.selectedVoiceId }
        speechLocale = AndroidSystemTtsSynthesizer.localeForVoiceId(voice?.id, voice?.name)
        textBeforeListen = state.text
        if (speechToTextEngine.hasGoogleSpeech()) {
            requestSystemSpeechIntent(appContext.getString(R.string.home_mic_opening))
        } else {
            startDeviceListening()
        }
    }

    fun createSpeechIntent(): android.content.Intent =
        speechToTextEngine.createRecognizeIntent(speechLocale)

    private fun startDeviceListening() {
        if (!speechToTextEngine.isAvailable()) {
            _uiState.update {
                it.copy(
                    isListening = false,
                    pendingSpeechIntent = false,
                    statusMessage = appContext.getString(R.string.home_mic_no_engine)
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isListening = true,
                pendingSpeechIntent = false,
                statusMessage = appContext.getString(R.string.home_mic_device)
            )
        }
        listenJob = viewModelScope.launch {
            speechToTextEngine.listen(speechLocale).collect { event ->
                when (event) {
                    SpeechEvent.Ready,
                    SpeechEvent.BeginningOfSpeech -> {
                        _uiState.update {
                            it.copy(statusMessage = appContext.getString(R.string.home_mic_device))
                        }
                    }
                    is SpeechEvent.Partial -> {
                        _uiState.update {
                            it.copy(
                                text = mergeTranscript(textBeforeListen, event.text),
                                statusMessage = appContext.getString(R.string.home_mic_device)
                            )
                        }
                    }
                    is SpeechEvent.Final -> {
                        _uiState.update {
                            it.copy(
                                text = mergeTranscript(textBeforeListen, event.text),
                                isListening = false,
                                statusMessage = appContext.getString(R.string.home_mic_done)
                            )
                        }
                    }
                    is SpeechEvent.Error,
                    is SpeechEvent.NeedsIntentFallback -> {
                        val message = when (event) {
                            is SpeechEvent.Error -> event.message
                            is SpeechEvent.NeedsIntentFallback -> event.message
                            else -> appContext.getString(R.string.home_mic_no_engine)
                        }
                        _uiState.update {
                            it.copy(isListening = false, statusMessage = message)
                        }
                    }
                    SpeechEvent.End -> {
                        _uiState.update { current ->
                            if (!current.isListening) current
                            else current.copy(
                                isListening = false,
                                statusMessage = if (current.text != textBeforeListen) {
                                    appContext.getString(R.string.home_mic_done)
                                } else {
                                    current.statusMessage
                                }
                            )
                        }
                    }
                }
            }
            _uiState.update { if (it.isListening) it.copy(isListening = false) else it }
        }
    }

    fun onSpeechIntentConsumed() {
        _uiState.update { it.copy(pendingSpeechIntent = false) }
    }

    fun onSpeechIntentLaunchFailed() {
        _uiState.update {
            it.copy(
                isListening = false,
                pendingSpeechIntent = false,
                statusMessage = appContext.getString(R.string.home_mic_unavailable)
            )
        }
    }

    fun onSpeechIntentResult(data: android.content.Intent?) {
        val spoken = speechToTextEngine.parseRecognizeResult(data)
        _uiState.update { state ->
            if (spoken.isNullOrBlank()) {
                state.copy(
                    isListening = false,
                    pendingSpeechIntent = false,
                    statusMessage = "Không nhận được lời nói. Thử lại."
                )
            } else {
                state.copy(
                    text = mergeTranscript(state.text, spoken),
                    isListening = false,
                    pendingSpeechIntent = false,
                    statusMessage = "Đã chuyển giọng nói thành văn bản."
                )
            }
        }
    }

    private fun requestSystemSpeechIntent(message: String) {
        _uiState.update {
            it.copy(
                isListening = true,
                pendingSpeechIntent = true,
                statusMessage = message
            )
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _uiState.update {
            it.copy(
                isListening = false,
                pendingSpeechIntent = false,
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
            val textToSpeak = when (state.readMode) {
                HomeReadMode.ORIGINAL -> {
                    _uiState.update { it.copy(translatedText = "", isTranslating = false) }
                    text
                }
                HomeReadMode.TRANSLATE_AND_READ -> resolveTextForTranslateAndRead(text, state.selectedTargetLang)
            }
            if (textToSpeak.isBlank()) return@launch

            _uiState.update {
                it.copy(
                    isPlaying = true,
                    statusMessage = "Đang phát…",
                    showSystemTtsDialog = if (suppress) false else advice.suggestOpenModelManager
                )
            }
            // Clear STT/SCO earpiece routing before TTS media playback.
            audioRoutingManager.ensureMediaMode()
            playbackController.setSpeed(state.playbackSpeed)
            TtsPlaybackStarter.start(appContext)
            playbackController.start(textToSpeak, voice, title = voice.name)
        }
    }

    private suspend fun resolveTextForTranslateAndRead(
        source: String,
        targetLang: String
    ): String {
        _uiState.update {
            it.copy(isTranslating = true, statusMessage = "Đang nhận diện ngôn ngữ…")
        }
        val detected = languageIdentifier.identify(source)
        val targetLabel = LanguageCodes.nativeLabel(targetLang)
        _uiState.update {
            it.copy(statusMessage = "Đang dịch sang $targetLabel…")
        }
        return translationRepository.translate(
            text = source,
            sourceLang = detected,
            targetLang = targetLang
        ).fold(
            onSuccess = { translated ->
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        translatedText = translated,
                        statusMessage = "Sẵn sàng phát bản dịch."
                    )
                }
                translated
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        translatedText = "",
                        statusMessage = e.message ?: "Dịch thất bại — phát bản gốc."
                    )
                }
                source
            }
        )
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
