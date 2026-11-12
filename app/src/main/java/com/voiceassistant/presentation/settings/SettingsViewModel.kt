package com.voiceassistant.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.prefs.ApiKeyStore
import com.voiceassistant.data.prefs.LanguagePreferences
import com.voiceassistant.domain.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeyInput: String = "",
    val hasStoredKey: Boolean = false,
    val apiKeyMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languagePreferences: LanguagePreferences,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    val language: StateFlow<AppLanguage> = languagePreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.fromSystem())

    val supportedLanguages: List<AppLanguage> = AppLanguage.entries

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            apiKeyInput = "",
            hasStoredKey = apiKeyStore.hasUserKey()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch {
            languagePreferences.setLanguage(language)
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKeyInput = value, apiKeyMessage = "") }
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKeyInput.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(apiKeyMessage = "Nhập API key trước khi lưu.") }
            return
        }
        apiKeyStore.setGeminiApiKey(key)
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                hasStoredKey = true,
                apiKeyMessage = "Đã lưu Gemini API key."
            )
        }
    }

    fun clearApiKey() {
        apiKeyStore.clearGeminiApiKey()
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                hasStoredKey = false,
                apiKeyMessage = "Đã xóa API key. Có thể dùng key trong local.properties nếu có."
            )
        }
    }
}
