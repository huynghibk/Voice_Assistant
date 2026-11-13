package com.voiceassistant.presentation.quick

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.ingress.PendingTextIngress
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.playback.TtsPlaybackStarter
import com.voiceassistant.engine.translation.LanguageCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickProcessUiState(
    val sourceText: String = "",
    val translatedText: String = "",
    val detectedLabel: String = "",
    val detectedLang: String = LanguageIdentifier.FALLBACK,
    val isWorking: Boolean = false,
    val isPlaying: Boolean = false,
    val statusMessage: String = ""
)

@HiltViewModel
class QuickProcessTextViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val languageIdentifier: LanguageIdentifier,
    private val translationRepository: TranslationRepository,
    private val playbackController: PlaybackController,
    private val voiceRepository: VoiceRepository,
    private val pendingTextIngress: PendingTextIngress
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickProcessUiState())
    val uiState: StateFlow<QuickProcessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                _uiState.update { it.copy(isPlaying = playback.isPlaying) }
            }
        }
    }

    fun bindSourceText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Không có văn bản.") }
            return
        }
        _uiState.update {
            it.copy(sourceText = trimmed, isWorking = true, statusMessage = "Đang nhận diện…")
        }
        viewModelScope.launch {
            val detected = languageIdentifier.identify(trimmed)
            _uiState.update {
                it.copy(
                    detectedLang = detected,
                    detectedLabel = LanguageCodes.nativeLabel(detected),
                    statusMessage = "Đang dịch…"
                )
            }
            translationRepository.translate(
                text = trimmed,
                sourceLang = detected,
                targetLang = "vi"
            ).fold(
                onSuccess = { translated ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            translatedText = translated,
                            statusMessage = "Sẵn sàng nghe hoặc mở trong app."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            translatedText = trimmed,
                            statusMessage = e.message ?: "Dịch thất bại — giữ bản gốc."
                        )
                    }
                }
            )
        }
    }

    fun playTranslation() {
        val text = _uiState.value.translatedText.ifBlank { _uiState.value.sourceText }.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            val voice = voiceRepository.getDefaultVoice()
                ?: voiceRepository.observeVoices().first().firstOrNull()
            if (voice == null) {
                _uiState.update { it.copy(statusMessage = "Chưa có giọng đọc.") }
                return@launch
            }
            if (_uiState.value.isPlaying) {
                playbackController.stop()
                TtsPlaybackStarter.stop(appContext)
                return@launch
            }
            TtsPlaybackStarter.start(appContext)
            playbackController.start(text, voice, title = "Quick Translate")
        }
    }

    fun openInAppTts() {
        val text = _uiState.value.translatedText.ifBlank { _uiState.value.sourceText }.trim()
        if (text.isEmpty()) return
        pendingTextIngress.offerTts(text)
    }

    fun openInAppTranslate() {
        val text = _uiState.value.sourceText.trim()
        if (text.isEmpty()) return
        pendingTextIngress.offerTranslate(text)
    }
}
