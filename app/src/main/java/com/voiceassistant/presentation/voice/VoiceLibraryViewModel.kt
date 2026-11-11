package com.voiceassistant.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceLibraryViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

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
            voiceRepository.upsert(current.copy(name = name.trim().ifEmpty { current.name }))
        }
    }
}
