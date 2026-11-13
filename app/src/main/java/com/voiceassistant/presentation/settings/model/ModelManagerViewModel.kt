package com.voiceassistant.presentation.settings.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.resource.MlKitLanguagePack
import com.voiceassistant.domain.resource.MlKitPairPack
import com.voiceassistant.domain.resource.PiperVoicePack
import com.voiceassistant.domain.resource.ResourcePackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelManagerUiState(
    val piperPacks: List<PiperVoicePack> = emptyList(),
    val mlKitLanguages: List<MlKitLanguagePack> = emptyList(),
    val recommendedPairs: List<MlKitPairPack> = emptyList(),
    val busyPackId: String? = null,
    val statusMessage: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val resourcePackManager: ResourcePackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val piper = resourcePackManager.listPiperPacks()
                val langs = resourcePackManager.listMlKitLanguages()
                val pairs = resourcePackManager.listRecommendedPairs()
                _uiState.update {
                    it.copy(
                        piperPacks = piper,
                        mlKitLanguages = langs,
                        recommendedPairs = pairs,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = e.message ?: "Không tải được danh sách gói."
                    )
                }
            }
        }
    }

    fun downloadPiper(packId: String) {
        viewModelScope.launch {
            val pack = _uiState.value.piperPacks.firstOrNull { it.id == packId }
            val name = pack?.displayName ?: packId
            val sizeMb = pack?.estimatedSizeMb ?: 63
            _uiState.update {
                it.copy(
                    busyPackId = packId,
                    statusMessage = "Đang tải giọng $name (~${sizeMb}MB)…"
                )
            }
            runCatching {
                resourcePackManager.downloadPiperVoice(packId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        busyPackId = null,
                        statusMessage = "Đã tải $name. Ứng dụng sẽ dùng Piper ONNX."
                    )
                }
                refresh()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        busyPackId = null,
                        statusMessage = e.message ?: "Tải giọng Piper thất bại."
                    )
                }
            }
        }
    }

    fun downloadLanguage(code: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyPackId = code,
                    statusMessage = "Đang tải $code…"
                )
            }
            runCatching {
                resourcePackManager.downloadMlKitLanguage(code, requireWifi = false)
            }.onSuccess {
                _uiState.update { it.copy(busyPackId = null, statusMessage = "Đã tải $code.") }
                refresh()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        busyPackId = null,
                        statusMessage = e.message ?: "Tải thất bại."
                    )
                }
            }
        }
    }

    fun deleteLanguage(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPackId = code, statusMessage = "Đang xóa $code…") }
            runCatching {
                resourcePackManager.deleteMlKitLanguage(code)
            }.onSuccess {
                _uiState.update { it.copy(busyPackId = null, statusMessage = "Đã xóa $code.") }
                refresh()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        busyPackId = null,
                        statusMessage = e.message ?: "Xóa thất bại."
                    )
                }
            }
        }
    }

    fun downloadPair(source: String, target: String) {
        viewModelScope.launch {
            val id = "$source-$target"
            _uiState.update {
                it.copy(busyPackId = id, statusMessage = "Đang tải cặp $source ↔ $target…")
            }
            runCatching {
                resourcePackManager.downloadMlKitLanguage(source, requireWifi = false)
                resourcePackManager.downloadMlKitLanguage(target, requireWifi = false)
            }.onSuccess {
                _uiState.update {
                    it.copy(busyPackId = null, statusMessage = "Đã tải cặp $source ↔ $target.")
                }
                refresh()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        busyPackId = null,
                        statusMessage = e.message ?: "Tải cặp thất bại."
                    )
                }
            }
        }
    }
}
