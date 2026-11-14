package com.voiceassistant.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.BuildConfig
import com.voiceassistant.data.prefs.ApiKeyStore
import com.voiceassistant.data.prefs.LanguagePreferences
import com.voiceassistant.data.prefs.TtsPreferences
import com.voiceassistant.domain.model.AppLanguage
import com.voiceassistant.domain.tts.TtsBackend
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
    val storedKeyMask: String = "",
    val keyFromBuildConfig: Boolean = false,
    val keyBundledInApp: Boolean = false,
    val apiKeyMessage: String = "",
    val ttsBackend: TtsBackend = TtsBackend.SYSTEM_GOOGLE,
    val googleCloudTtsInput: String = "",
    val hasGoogleCloudTtsKey: Boolean = false,
    val googleCloudTtsMask: String = "",
    val elevenLabsInput: String = "",
    val hasElevenLabsKey: Boolean = false,
    val elevenLabsMask: String = "",
    val ttsKeyMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languagePreferences: LanguagePreferences,
    private val apiKeyStore: ApiKeyStore,
    private val ttsPreferences: TtsPreferences
) : ViewModel() {

    val language: StateFlow<AppLanguage> = languagePreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.fromSystem())

    val ttsBackendFlow: StateFlow<TtsBackend> = ttsPreferences.backend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsBackend.SYSTEM_GOOGLE)

    val supportedLanguages: List<AppLanguage> = AppLanguage.entries
    val supportedTtsBackends: List<TtsBackend> = TtsBackend.entries

    private val _uiState = MutableStateFlow(refreshKeyUi())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ttsBackendFlow.collect { backend ->
                _uiState.update { it.copy(ttsBackend = backend) }
            }
        }
    }

    private fun refreshKeyUi(): SettingsUiState {
        val user = apiKeyStore.hasUserKey()
        val fromBuild = BuildConfig.GEMINI_API_KEY.isNotBlank()
        val bundled = !user && fromBuild && BuildConfig.GEMINI_KEY_BUNDLED
        return SettingsUiState(
            hasStoredKey = user || fromBuild,
            storedKeyMask = when {
                user -> apiKeyStore.maskedKeyPreview()
                bundled -> maskBuildConfigKey()
                fromBuild -> "local.properties"
                else -> ""
            },
            keyFromBuildConfig = !user && fromBuild && !bundled,
            keyBundledInApp = bundled,
            hasGoogleCloudTtsKey = apiKeyStore.hasGoogleCloudTtsKey(),
            googleCloudTtsMask = apiKeyStore.maskedGoogleCloudTtsKeyPreview(),
            hasElevenLabsKey = apiKeyStore.hasElevenLabsKey(),
            elevenLabsMask = apiKeyStore.maskedElevenLabsKeyPreview()
        )
    }

    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch {
            languagePreferences.setLanguage(language)
        }
    }

    fun onTtsBackendSelected(backend: TtsBackend) {
        viewModelScope.launch {
            ttsPreferences.setBackend(backend)
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
            refreshKeyUi().copy(
                apiKeyInput = "",
                apiKeyMessage = "Đã lưu Gemini API key."
            )
        }
    }

    fun clearApiKey() {
        apiKeyStore.clearGeminiApiKey()
        _uiState.update {
            refreshKeyUi().copy(
                apiKeyInput = "",
                apiKeyMessage = if (BuildConfig.GEMINI_KEY_BUNDLED) {
                    "Đã xóa key cá nhân. App dùng key mặc định tích hợp sẵn."
                } else {
                    "Đã xóa API key. Có thể dùng key trong local.properties nếu có."
                }
            )
        }
    }

    private fun maskBuildConfigKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.length <= 10) return "••••"
        return key.take(4) + "•••" + key.takeLast(4)
    }

    fun onGoogleCloudTtsKeyChange(value: String) {
        _uiState.update { it.copy(googleCloudTtsInput = value, ttsKeyMessage = "") }
    }

    fun saveGoogleCloudTtsKey() {
        val key = _uiState.value.googleCloudTtsInput.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(ttsKeyMessage = "Nhập Google Cloud TTS API key trước.") }
            return
        }
        apiKeyStore.setGoogleCloudTtsApiKey(key)
        _uiState.update {
            refreshKeyUi().copy(
                googleCloudTtsInput = "",
                ttsKeyMessage = "Đã lưu Google Cloud TTS API key."
            )
        }
    }

    fun clearGoogleCloudTtsKey() {
        apiKeyStore.clearGoogleCloudTtsApiKey()
        _uiState.update {
            refreshKeyUi().copy(googleCloudTtsInput = "", ttsKeyMessage = "Đã xóa Google Cloud TTS key.")
        }
    }

    fun onElevenLabsKeyChange(value: String) {
        _uiState.update { it.copy(elevenLabsInput = value, ttsKeyMessage = "") }
    }

    fun saveElevenLabsKey() {
        val key = _uiState.value.elevenLabsInput.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(ttsKeyMessage = "Nhập ElevenLabs API key trước.") }
            return
        }
        apiKeyStore.setElevenLabsApiKey(key)
        _uiState.update {
            refreshKeyUi().copy(
                elevenLabsInput = "",
                ttsKeyMessage = "Đã lưu ElevenLabs API key."
            )
        }
    }

    fun clearElevenLabsKey() {
        apiKeyStore.clearElevenLabsApiKey()
        _uiState.update {
            refreshKeyUi().copy(elevenLabsInput = "", ttsKeyMessage = "Đã xóa ElevenLabs key.")
        }
    }

    fun ttsBackendLabelRes(backend: TtsBackend): Int = when (backend) {
        TtsBackend.SYSTEM_GOOGLE -> com.voiceassistant.R.string.settings_tts_backend_system
        TtsBackend.OFFLINE_ONNX -> com.voiceassistant.R.string.settings_tts_backend_offline
        TtsBackend.CLOUD_GOOGLE -> com.voiceassistant.R.string.settings_tts_backend_cloud_google
        TtsBackend.CLOUD_ELEVENLABS -> com.voiceassistant.R.string.settings_tts_backend_cloud_elevenlabs
    }
}
