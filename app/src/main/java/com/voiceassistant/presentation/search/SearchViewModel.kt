package com.voiceassistant.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.local.db.SearchHistoryDao
import com.voiceassistant.data.local.db.SearchHistoryEntity
import com.voiceassistant.data.network.NetworkMonitor
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.search.AiSearchEngine
import com.voiceassistant.domain.usecase.SynthesizeSpeechUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val prompt: String = "",
    val latestResponse: String = "",
    val latestSource: String = "",
    val isSearching: Boolean = false,
    val isSpeaking: Boolean = false,
    val isOnline: Boolean = true,
    val status: String = "Hỏi về dự án nội bộ hoặc tìm trên web.",
    val voices: List<VoiceModel> = emptyList(),
    val selectedVoiceId: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val aiSearchEngine: AiSearchEngine,
    private val searchHistoryDao: SearchHistoryDao,
    private val networkMonitor: NetworkMonitor,
    private val voiceRepository: VoiceRepository,
    private val synthesizeSpeechUseCase: SynthesizeSpeechUseCase
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState(isOnline = networkMonitor.isOnline()))
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    val history: StateFlow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent(100)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var speakJob: Job? = null

    init {
        viewModelScope.launch {
            voiceRepository.seedPresetsIfEmpty()
            voiceRepository.observeVoices().collect { voices ->
                val selected = _ui.value.selectedVoiceId
                    ?: voices.firstOrNull { it.isDefault }?.id
                    ?: voices.firstOrNull()?.id
                _ui.update {
                    it.copy(
                        voices = voices,
                        selectedVoiceId = selected,
                        isOnline = networkMonitor.isOnline()
                    )
                }
            }
        }
    }

    fun onPromptChange(value: String) = _ui.update { it.copy(prompt = value) }

    fun onVoiceSelected(id: String) = _ui.update { it.copy(selectedVoiceId = id) }

    fun submit() {
        val prompt = _ui.value.prompt.trim()
        if (prompt.isEmpty()) {
            _ui.update { it.copy(status = "Nhập câu hỏi / prompt.") }
            return
        }
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isSearching = true,
                    isOnline = networkMonitor.isOnline(),
                    status = if (networkMonitor.isOnline()) "Đang tìm (web + nội bộ)…"
                    else "Offline — chỉ tìm trong dự án…"
                )
            }
            try {
                val answer = aiSearchEngine.search(prompt, preferWeb = true)
                _ui.update {
                    it.copy(
                        isSearching = false,
                        latestResponse = answer.response,
                        latestSource = answer.source,
                        status = "Nguồn: ${answer.source}",
                        prompt = ""
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(isSearching = false, status = e.message ?: "Tìm kiếm thất bại")
                }
            }
        }
    }

    fun speakLatest() {
        val text = _ui.value.latestResponse.trim()
        if (text.isEmpty()) {
            _ui.update { it.copy(status = "Chưa có kết quả để đọc.") }
            return
        }
        val voice = _ui.value.voices.firstOrNull { it.id == _ui.value.selectedVoiceId } ?: return
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            _ui.update { it.copy(isSpeaking = true, status = "Đang đọc kết quả…") }
            try {
                synthesizeSpeechUseCase.execute(text, voice).collect { progress ->
                    _ui.update {
                        it.copy(
                            status = "Đọc câu ${progress.chunk.sentenceIndex + 1}/${progress.chunk.sentenceCount}"
                        )
                    }
                }
                _ui.update { it.copy(isSpeaking = false, status = "Đã đọc xong.") }
            } catch (e: Exception) {
                synthesizeSpeechUseCase.stop()
                _ui.update {
                    it.copy(isSpeaking = false, status = e.message ?: "Đã dừng đọc.")
                }
            }
        }
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        viewModelScope.launch {
            synthesizeSpeechUseCase.stop()
            _ui.update { it.copy(isSpeaking = false, status = "Đã dừng đọc.") }
        }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch { searchHistoryDao.delete(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryDao.clear() }
    }

    fun reusePrompt(prompt: String) {
        _ui.update { it.copy(prompt = prompt) }
    }
}
