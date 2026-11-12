package com.voiceassistant.presentation.translate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.engine.playback.TtsPlaybackStarter
import com.voiceassistant.engine.stt.SpeechEvent
import com.voiceassistant.engine.stt.SpeechToTextEngine
import com.voiceassistant.engine.translation.LanguageCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class ConversationSide { MINE, THEIRS }

data class ConversationUiState(
    val languages: List<OfflineTranslationService.Lang> = emptyList(),
    val myLang: String = "vi",
    val theirLang: String = "en",
    val myText: String = "",
    val theirText: String = "",
    val isListening: Boolean = false,
    val listeningSide: ConversationSide? = null,
    val isTranslating: Boolean = false,
    val isSpeaking: Boolean = false,
    val autoSpeak: Boolean = true,
    val statusMessage: String = "",
    val voices: List<VoiceModel> = emptyList()
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val translationRepository: TranslationRepository,
    private val offlineTranslationService: OfflineTranslationService,
    private val speechToTextEngine: SpeechToTextEngine,
    private val playbackController: PlaybackController,
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConversationUiState(languages = offlineTranslationService.supported)
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var translateJob: Job? = null

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

    fun onMyLang(id: String) {
        if (id == _uiState.value.theirLang) return
        _uiState.update { it.copy(myLang = id) }
    }

    fun onTheirLang(id: String) {
        if (id == _uiState.value.myLang) return
        _uiState.update { it.copy(theirLang = id) }
    }

    fun swapLanguages() {
        _uiState.update { state ->
            state.copy(
                myLang = state.theirLang,
                theirLang = state.myLang,
                myText = state.theirText,
                theirText = state.myText
            )
        }
    }

    fun toggleAutoSpeak() {
        _uiState.update { it.copy(autoSpeak = !it.autoSpeak) }
    }

    fun toggleListening(side: ConversationSide) {
        val state = _uiState.value
        if (state.isListening && state.listeningSide == side) {
            stopListening()
        } else {
            startListening(side)
        }
    }

    fun startListening(side: ConversationSide) {
        stopPlayback()
        if (!speechToTextEngine.isAvailable()) {
            _uiState.update {
                it.copy(statusMessage = "Thiết bị không hỗ trợ nhận dạng giọng nói.")
            }
            return
        }

        listenJob?.cancel()
        translateJob?.cancel()

        val sourceLang = if (side == ConversationSide.MINE) {
            _uiState.value.myLang
        } else {
            _uiState.value.theirLang
        }

        _uiState.update {
            it.copy(
                isListening = true,
                listeningSide = side,
                isTranslating = false,
                statusMessage = "Đang nghe… hãy nói."
            )
        }

        listenJob = viewModelScope.launch {
            speechToTextEngine.listen(localeFor(sourceLang)).collect { event ->
                when (event) {
                    SpeechEvent.Ready,
                    SpeechEvent.BeginningOfSpeech -> {
                        _uiState.update { it.copy(statusMessage = "Đang nghe…") }
                    }
                    is SpeechEvent.Partial -> {
                        applyTranscript(side, event.text, provisional = true)
                    }
                    is SpeechEvent.Final -> {
                        applyTranscript(side, event.text, provisional = false)
                        _uiState.update {
                            it.copy(isListening = false, listeningSide = null)
                        }
                        translateAndMaybeSpeak(side, event.text.trim())
                    }
                    is SpeechEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                listeningSide = null,
                                statusMessage = event.message
                            )
                        }
                    }
                    SpeechEvent.End -> {
                        _uiState.update { state ->
                            if (state.isListening) {
                                state.copy(
                                    isListening = false,
                                    listeningSide = null,
                                    statusMessage = state.statusMessage.ifBlank { "Đã dừng ghi âm." }
                                )
                            } else state
                        }
                    }
                }
            }
            _uiState.update {
                if (it.isListening) {
                    it.copy(isListening = false, listeningSide = null)
                } else it
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _uiState.update {
            it.copy(
                isListening = false,
                listeningSide = null,
                statusMessage = "Đã dừng ghi âm."
            )
        }
    }

    fun stopPlayback() {
        playbackController.stop()
        TtsPlaybackStarter.stop(appContext)
    }

    private fun applyTranscript(side: ConversationSide, text: String, provisional: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            when (side) {
                ConversationSide.MINE -> state.copy(
                    myText = trimmed,
                    statusMessage = if (provisional) "Đang nghe…" else state.statusMessage
                )
                ConversationSide.THEIRS -> state.copy(
                    theirText = trimmed,
                    statusMessage = if (provisional) "Đang nghe…" else state.statusMessage
                )
            }
        }
    }

    private fun translateAndMaybeSpeak(side: ConversationSide, sourceText: String) {
        if (sourceText.isBlank()) return
        val state = _uiState.value
        val sourceLang = if (side == ConversationSide.MINE) state.myLang else state.theirLang
        val targetLang = if (side == ConversationSide.MINE) state.theirLang else state.myLang

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, statusMessage = "Đang dịch…") }
            translationRepository.translate(
                text = sourceText,
                sourceLang = sourceLang,
                targetLang = targetLang
            ).fold(
                onSuccess = { translated ->
                    _uiState.update { current ->
                        when (side) {
                            ConversationSide.MINE -> current.copy(
                                myText = sourceText,
                                theirText = translated,
                                isTranslating = false,
                                statusMessage = "Đã dịch."
                            )
                            ConversationSide.THEIRS -> current.copy(
                                theirText = sourceText,
                                myText = translated,
                                isTranslating = false,
                                statusMessage = "Đã dịch."
                            )
                        }
                    }
                    if (_uiState.value.autoSpeak && translated.isNotBlank()) {
                        speakTranslation(translated, targetLang)
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

    private fun speakTranslation(text: String, targetLang: String) {
        val voice = voiceFor(targetLang) ?: return
        stopPlayback()
        playbackController.start(text, voice, title = "Conversation")
        TtsPlaybackStarter.start(appContext)
    }

    private fun voiceFor(langId: String): VoiceModel? {
        val voices = _uiState.value.voices
        if (voices.isEmpty()) return null
        val key = langId.lowercase(Locale.ROOT)
        return voices.firstOrNull { voice ->
            voice.tags.any { it.equals(key, ignoreCase = true) } ||
                voice.id.contains(key, ignoreCase = true) ||
                voice.name.contains(LanguageCodes.nativeLabel(langId), ignoreCase = true)
        } ?: voices.firstOrNull { it.isDefault } ?: voices.firstOrNull()
    }

    private fun localeFor(langId: String): Locale {
        val key = langId.trim().lowercase(Locale.ROOT).replace('_', '-')
        return when {
            key == "vi" || key.startsWith("vi-") -> Locale("vi", "VN")
            key == "en" || key.startsWith("en-") -> Locale.US
            key == "zh-tw" || key.startsWith("zh-hant") || key.startsWith("zh-hk") ->
                Locale.TRADITIONAL_CHINESE
            key.startsWith("zh") -> Locale.SIMPLIFIED_CHINESE
            key == "ja" || key.startsWith("ja-") -> Locale.JAPANESE
            key == "ko" || key.startsWith("ko-") -> Locale.KOREAN
            key == "fr" || key.startsWith("fr-") -> Locale.FRENCH
            else -> Locale.forLanguageTag(langId)
        }
    }

    override fun onCleared() {
        listenJob?.cancel()
        translateJob?.cancel()
        stopPlayback()
        super.onCleared()
    }
}
