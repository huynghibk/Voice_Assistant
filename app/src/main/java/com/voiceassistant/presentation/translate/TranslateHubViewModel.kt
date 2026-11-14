package com.voiceassistant.presentation.translate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.playback.TtsPlaybackStarter
import com.voiceassistant.engine.stt.SpeechEvent
import com.voiceassistant.engine.stt.SpeechToTextEngine
import com.voiceassistant.R
import com.voiceassistant.engine.translation.LanguageCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class TranslateHubUiState(
    val text: String = "",
    val translatedText: String = "",
    val languages: List<OfflineTranslationService.Lang> = emptyList(),
    val selectedSourceLang: String = LanguageIdentifier.AUTO,
    val selectedTargetLang: String = "vi",
    val detectedSourceLang: String? = null,
    val detectedLabel: String = "",
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val statusMessage: String = "",
    val showDownloadDialog: Boolean = false,
    val downloadDialogMessage: String = "",
    val pendingSourceCode: String? = null,
    val pendingTargetCode: String? = null,
    val isDownloadingPack: Boolean = false,
    val voices: List<VoiceModel> = emptyList()
)

@HiltViewModel
class TranslateHubViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val translationRepository: TranslationRepository,
    private val offlineTranslationService: OfflineTranslationService,
    private val ttsResourceGuard: TtsResourceGuard,
    private val resourcePackManager: ResourcePackManager,
    private val languageIdentifier: LanguageIdentifier,
    private val speechToTextEngine: SpeechToTextEngine,
    private val playbackController: PlaybackController,
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TranslateHubUiState(languages = offlineTranslationService.supported)
    )
    val uiState: StateFlow<TranslateHubUiState> = _uiState.asStateFlow()

    private var translateJob: Job? = null
    private var detectJob: Job? = null
    private var listenJob: Job? = null
    private var resolvedSourceForTranslate: String = LanguageIdentifier.FALLBACK

    init {
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.observeVoices().collect { voices ->
                _uiState.update { it.copy(voices = voices) }
            }
        }
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                _uiState.update { it.copy(isSpeaking = playback.isPlaying && !playback.isPaused) }
            }
        }
    }

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value) }
        scheduleDetectIfAuto(value)
    }

    fun clearSource() {
        _uiState.update {
            it.copy(text = "", detectedSourceLang = null, detectedLabel = "", statusMessage = "")
        }
    }

    fun pasteSource(value: String) {
        if (value.isBlank()) return
        onTextChange(value)
        _uiState.update { it.copy(statusMessage = "Đã dán văn bản.") }
    }

    fun onSourceLang(id: String) {
        _uiState.update {
            it.copy(
                selectedSourceLang = id,
                detectedSourceLang = if (id == LanguageIdentifier.AUTO) it.detectedSourceLang else null,
                detectedLabel = if (id == LanguageIdentifier.AUTO) it.detectedLabel else ""
            )
        }
        if (id == LanguageIdentifier.AUTO) {
            scheduleDetectIfAuto(_uiState.value.text)
        }
    }

    fun onTargetLang(id: String) = _uiState.update { it.copy(selectedTargetLang = id) }

    fun swapLanguages() {
        viewModelScope.launch {
            val before = _uiState.value
            val newSource = if (before.selectedSourceLang == LanguageIdentifier.AUTO) {
                before.detectedSourceLang?.takeIf { it.isNotBlank() }
                    ?: languageIdentifier.identify(before.text)
            } else {
                before.selectedSourceLang
            }
            val incoming = before.translatedText.ifBlank { before.text }
            _uiState.update {
                it.copy(
                    selectedSourceLang = before.selectedTargetLang,
                    selectedTargetLang = newSource,
                    text = incoming,
                    translatedText = if (before.translatedText.isNotBlank()) before.text else "",
                    detectedSourceLang = null,
                    detectedLabel = ""
                )
            }
            if (incoming.isNotBlank()) translate()
        }
    }

    fun applyIncomingText(value: String) {
        if (value.isBlank()) return
        _uiState.update {
            it.copy(text = value, statusMessage = "Đã nhận văn bản từ camera.")
        }
        viewModelScope.launch { detectNow(value) }
    }

    fun translate() {
        val state = _uiState.value
        val text = state.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Nhập văn bản để dịch.") }
            return
        }
        viewModelScope.launch {
            val source = resolveSourceLang(text)
            if (source == state.selectedTargetLang) {
                _uiState.update { it.copy(statusMessage = "Ngôn ngữ nguồn và đích trùng nhau.") }
                return@launch
            }
            resolvedSourceForTranslate = source
            val advice = ttsResourceGuard.adviseTranslate(source, state.selectedTargetLang)
            if (advice.needsDownload) {
                _uiState.update {
                    it.copy(
                        showDownloadDialog = true,
                        downloadDialogMessage = advice.message.orEmpty(),
                        pendingSourceCode = advice.sourceCode,
                        pendingTargetCode = advice.targetCode
                    )
                }
            } else {
                runTranslate(source)
            }
        }
    }

    fun dismissDownloadDialog() {
        _uiState.update {
            it.copy(
                showDownloadDialog = false,
                isDownloadingPack = false,
                pendingSourceCode = null,
                pendingTargetCode = null
            )
        }
    }

    fun continueTranslateWithoutDownload() {
        dismissDownloadDialog()
        runTranslate(resolvedSourceForTranslate, onlineOnly = true)
    }

    fun confirmDownloadAndTranslate() {
        val state = _uiState.value
        val src = state.pendingSourceCode ?: return
        val tgt = state.pendingTargetCode ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingPack = true, statusMessage = "Đang tải gói dịch…") }
            runCatching {
                resourcePackManager.downloadMlKitLanguage(src, requireWifi = false)
                resourcePackManager.downloadMlKitLanguage(tgt, requireWifi = false)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        showDownloadDialog = false,
                        isDownloadingPack = false,
                        pendingSourceCode = null,
                        pendingTargetCode = null
                    )
                }
                runTranslate(resolvedSourceForTranslate)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isDownloadingPack = false,
                        statusMessage = e.message ?: "Tải gói dịch thất bại."
                    )
                }
            }
        }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) stopListening() else startListening()
    }

    fun startListening() {
        if (_uiState.value.isSpeaking) stopSpeaking()
        if (!speechToTextEngine.isAvailable()) {
            _uiState.update {
                it.copy(statusMessage = "Thiết bị không hỗ trợ nhận dạng giọng nói. Thử micro hội thoại.")
            }
            return
        }
        listenJob?.cancel()
        val base = _uiState.value.text
        _uiState.update {
            it.copy(isListening = true, statusMessage = "Đang nghe… hãy nói.")
        }
        listenJob = viewModelScope.launch {
            val locale = localeFor(_uiState.value.selectedSourceLang)
            speechToTextEngine.listen(locale).collect { event ->
                when (event) {
                    SpeechEvent.Ready, SpeechEvent.BeginningOfSpeech -> {
                        _uiState.update { it.copy(statusMessage = "Đang nghe…") }
                    }
                    is SpeechEvent.Partial -> {
                        val merged = mergeTranscript(base, event.text)
                        _uiState.update { it.copy(text = merged) }
                    }
                    is SpeechEvent.Final -> {
                        val merged = mergeTranscript(base, event.text)
                        _uiState.update {
                            it.copy(
                                text = merged,
                                isListening = false,
                                statusMessage = "Đã chuyển giọng nói thành văn bản."
                            )
                        }
                        scheduleDetectIfAuto(merged)
                    }
                    is SpeechEvent.Error -> {
                        _uiState.update {
                            it.copy(isListening = false, statusMessage = event.message)
                        }
                    }
                    SpeechEvent.End -> {
                        _uiState.update { s ->
                            if (s.isListening) s.copy(isListening = false) else s
                        }
                    }
                    is SpeechEvent.NeedsIntentFallback -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                statusMessage = event.message
                            )
                        }
                    }
                }
            }
            _uiState.update { if (it.isListening) it.copy(isListening = false) else it }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _uiState.update { it.copy(isListening = false, statusMessage = "Đã dừng ghi âm.") }
    }

    fun speakTranslation() {
        val text = _uiState.value.translatedText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Chưa có bản dịch để nghe.") }
            return
        }
        val voice = voiceFor(_uiState.value.selectedTargetLang) ?: return
        stopSpeaking()
        TtsPlaybackStarter.start(appContext)
        playbackController.start(text, voice, title = "Translate")
        _uiState.update { it.copy(statusMessage = "Đang phát bản dịch…") }
    }

    fun stopSpeaking() {
        playbackController.stop()
        TtsPlaybackStarter.stop(appContext)
    }

    fun markCopied() {
        _uiState.update { it.copy(statusMessage = "Đã sao chép.") }
    }

    private fun mergeTranscript(base: String, spoken: String): String {
        val spokenTrim = spoken.trim()
        if (spokenTrim.isEmpty()) return base
        val baseTrim = base.trimEnd()
        return if (baseTrim.isEmpty()) spokenTrim else "$baseTrim $spokenTrim"
    }

    private fun voiceFor(langId: String): VoiceModel? {
        val voices = _uiState.value.voices
        if (voices.isEmpty()) return null
        val key = langId.lowercase(Locale.ROOT)
        return voices.firstOrNull { voice ->
            voice.tags.any { it.equals(key, ignoreCase = true) } ||
                voice.id.contains(key, ignoreCase = true)
        } ?: voices.firstOrNull { it.isDefault } ?: voices.firstOrNull()
    }

    private fun localeFor(langId: String): Locale {
        val key = langId.trim().lowercase(Locale.ROOT).replace('_', '-')
        return when {
            key == LanguageIdentifier.AUTO -> Locale.getDefault()
            key == "vi" || key.startsWith("vi-") -> Locale.forLanguageTag("vi-VN")
            key == "en" || key.startsWith("en-") -> Locale.US
            else -> Locale.forLanguageTag(langId)
        }
    }

    private fun scheduleDetectIfAuto(text: String) {
        if (_uiState.value.selectedSourceLang != LanguageIdentifier.AUTO) return
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            delay(400)
            detectNow(text)
        }
    }

    private suspend fun detectNow(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.selectedSourceLang != LanguageIdentifier.AUTO) {
            return
        }
        val detected = languageIdentifier.identify(trimmed)
        _uiState.update {
            it.copy(
                detectedSourceLang = detected,
                detectedLabel = LanguageCodes.nativeLabel(detected)
            )
        }
    }

    private suspend fun resolveSourceLang(text: String): String {
        val state = _uiState.value
        if (state.selectedSourceLang != LanguageIdentifier.AUTO) {
            return state.selectedSourceLang
        }
        val detected = languageIdentifier.identify(text)
        _uiState.update {
            it.copy(
                detectedSourceLang = detected,
                detectedLabel = LanguageCodes.nativeLabel(detected)
            )
        }
        return detected
    }

    private fun translateDoneMessage(): String = if (translationRepository.supportsOnlineAi()) {
        appContext.getString(R.string.translate_status_done_ai)
    } else {
        appContext.getString(R.string.translate_status_done_offline)
    }

    private fun runTranslate(sourceLang: String, onlineOnly: Boolean = false) {
        val state = _uiState.value
        val text = state.text.trim()
        if (text.isEmpty()) return
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            val status = if (onlineOnly) "Đang dịch trực tuyến…" else "Đang dịch…"
            _uiState.update { it.copy(isTranslating = true, statusMessage = status) }
            translationRepository.translate(
                text = text,
                sourceLang = sourceLang,
                targetLang = state.selectedTargetLang,
                onlineOnly = onlineOnly
            ).fold(
                onSuccess = { translated ->
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            translatedText = translated,
                            statusMessage = translateDoneMessage()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            statusMessage = e.message ?: "Dịch thất bại."
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        translateJob?.cancel()
        detectJob?.cancel()
        listenJob?.cancel()
        stopSpeaking()
        super.onCleared()
    }
}
