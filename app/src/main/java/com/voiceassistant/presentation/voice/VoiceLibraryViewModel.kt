package com.voiceassistant.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VoiceLibraryUiState(
    val clones: List<VoiceModel> = emptyList(),
    val presets: List<VoiceModel> = emptyList()
)

@HiltViewModel
class VoiceLibraryViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    val uiState: StateFlow<VoiceLibraryUiState> = voiceRepository.observeVoices()
        .map { list ->
            VoiceLibraryUiState(
                clones = list.filter { !it.isPreset },
                presets = list.filter { it.isPreset }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceLibraryUiState())

    val voices: StateFlow<List<VoiceModel>> = voiceRepository.observeVoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { voiceRepository.seedPresetsIfEmpty() }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { voiceRepository.setDefault(id) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val current = voiceRepository.getVoice(id) ?: return@launch
            if (current.isPreset) return@launch
            voiceRepository.upsert(current.copy(name = name.trim().ifEmpty { current.name }))
        }
    }

    fun deleteClone(id: String) {
        viewModelScope.launch {
            val voice = voiceRepository.getVoice(id) ?: return@launch
            if (voice.isPreset) return@launch
            voice.embeddingPath?.let { File(it).delete() }
            voice.sampleAudioPath?.let { File(it).delete() }
            val wasDefault = voice.isDefault
            voiceRepository.delete(id)
            if (wasDefault) {
                voiceRepository.listVoices()
                    .firstOrNull { it.isPreset }
                    ?.let { voiceRepository.setDefault(it.id) }
            }
        }
    }
}
