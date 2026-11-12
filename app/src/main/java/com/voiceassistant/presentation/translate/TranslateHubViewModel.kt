package com.voiceassistant.presentation.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.translation.LanguageCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val statusMessage: String = "",
    val showDownloadDialog: Boolean = false,
    val downloadDialogMessage: String = "",
    val pendingSourceCode: String? = null,
    val pendingTargetCode: String? = null,
    val isDownloadingPack: Boolean = false
)

@HiltViewModel
class TranslateHubViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val offlineTranslationService: OfflineTranslationService,
    private val ttsResourceGuard: TtsResourceGuard,
    private val resourcePackManager: ResourcePackManager,
    private val languageIdentifier: LanguageIdentifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TranslateHubUiState(languages = offlineTranslationService.supported)
    )
    val uiState: StateFlow<TranslateHubUiState> = _uiState.asStateFlow()

    private var translateJob: Job? = null
    private var detectJob: Job? = null
    private var resolvedSourceForTranslate: String = LanguageIdentifier.FALLBACK

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value) }
        scheduleDetectIfAuto(value)
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
        _uiState.update { state ->
            val newSource = when {
                state.selectedSourceLang == LanguageIdentifier.AUTO ->
                    state.detectedSourceLang ?: LanguageIdentifier.FALLBACK
                else -> state.selectedSourceLang
            }
            state.copy(
                selectedSourceLang = state.selectedTargetLang,
                selectedTargetLang = newSource,
                detectedSourceLang = null,
                detectedLabel = ""
            )
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
        runTranslate(resolvedSourceForTranslate)
    }

    fun confirmDownloadAndTranslate() {
        val state = _uiState.value
        val src = state.pendingSourceCode ?: return
        val tgt = state.pendingTargetCode ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingPack = true, statusMessage = "Đang tải gói dịch…") }
            runCatching {
                resourcePackManager.downloadMlKitLanguage(src, requireWifi = true)
                resourcePackManager.downloadMlKitLanguage(tgt, requireWifi = true)
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

    private fun runTranslate(sourceLang: String) {
        val state = _uiState.value
        val text = state.text.trim()
        if (text.isEmpty()) return
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, statusMessage = "Đang dịch…") }
            translationRepository.translate(
                text = text,
                sourceLang = sourceLang,
                targetLang = state.selectedTargetLang
            ).fold(
                onSuccess = { translated ->
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            translatedText = translated,
                            text = translated,
                            statusMessage = "Đã dịch — có thể gửi sang TTS."
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
        super.onCleared()
    }
}
