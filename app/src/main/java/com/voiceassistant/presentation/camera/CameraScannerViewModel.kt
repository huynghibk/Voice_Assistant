package com.voiceassistant.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.ocr.CameraOcrEngine
import com.voiceassistant.engine.translation.LanguageCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraScannerUiState(
    val isProcessing: Boolean = false,
    val ocrText: String = "",
    val translatedText: String = "",
    val statusMessage: String = "",
    val languages: List<OfflineTranslationService.Lang> = emptyList(),
    val selectedSourceLang: String = LanguageIdentifier.AUTO,
    val selectedTargetLang: String = "vi",
    val detectedSourceLang: String? = null,
    val detectedLabel: String = "",
    val captureRequested: Boolean = false
)

@HiltViewModel
class CameraScannerViewModel @Inject constructor(
    private val cameraOcrEngine: CameraOcrEngine,
    private val translationRepository: TranslationRepository,
    private val offlineTranslationService: OfflineTranslationService,
    private val languageIdentifier: LanguageIdentifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CameraScannerUiState(languages = offlineTranslationService.supported)
    )
    val uiState: StateFlow<CameraScannerUiState> = _uiState.asStateFlow()

    private var processJob: Job? = null

    fun onSourceLang(id: String) = _uiState.update {
        it.copy(
            selectedSourceLang = id,
            detectedSourceLang = if (id == LanguageIdentifier.AUTO) it.detectedSourceLang else null,
            detectedLabel = if (id == LanguageIdentifier.AUTO) it.detectedLabel else ""
        )
    }

    fun onTargetLang(id: String) = _uiState.update { it.copy(selectedTargetLang = id) }

    fun requestCapture() {
        if (_uiState.value.isProcessing) return
        _uiState.update {
            it.copy(
                captureRequested = true,
                statusMessage = "Đang chụp khung hình…"
            )
        }
    }

    fun consumeCaptureRequest(): Boolean {
        val requested = _uiState.value.captureRequested
        if (requested) {
            _uiState.update { it.copy(captureRequested = false) }
        }
        return requested
    }

    fun processFrame(image: InputImage) {
        if (_uiState.value.isProcessing) return
        processJob?.cancel()
        processJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, statusMessage = "Đang nhận dạng chữ…")
            }
            val ocrResult = runCatching { cameraOcrEngine.recognizeInput(image) }
            val ocrText = ocrResult.getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = e.message ?: "OCR thất bại."
                    )
                }
                return@launch
            }
            if (ocrText.isBlank()) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        ocrText = "",
                        statusMessage = "Không tìm thấy chữ trong khung hình."
                    )
                }
                return@launch
            }

            val state = _uiState.value
            val source = if (state.selectedSourceLang == LanguageIdentifier.AUTO) {
                val detected = languageIdentifier.identify(ocrText)
                _uiState.update {
                    it.copy(
                        ocrText = ocrText,
                        detectedSourceLang = detected,
                        detectedLabel = LanguageCodes.nativeLabel(detected),
                        statusMessage = "Đang dịch…"
                    )
                }
                detected
            } else {
                _uiState.update {
                    it.copy(ocrText = ocrText, statusMessage = "Đang dịch…")
                }
                state.selectedSourceLang
            }

            val translated = translationRepository.translate(
                text = ocrText,
                sourceLang = source,
                targetLang = _uiState.value.selectedTargetLang
            )
            translated.fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            translatedText = text,
                            statusMessage = "Xong — gửi sang TTS khi sẵn sàng."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            translatedText = ocrText,
                            statusMessage = "Dịch lỗi, giữ bản OCR: ${e.message ?: ""}"
                        )
                    }
                }
            )
        }
    }

    fun textForTts(): String {
        val state = _uiState.value
        return state.translatedText.ifBlank { state.ocrText }.trim()
    }
}
