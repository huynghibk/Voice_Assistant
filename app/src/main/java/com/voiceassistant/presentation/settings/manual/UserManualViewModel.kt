package com.voiceassistant.presentation.settings.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManualUiState(
    val chapters: List<ManualChapter> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class UserManualViewModel @Inject constructor(
    private val userManualRepository: UserManualRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManualUiState())
    val uiState: StateFlow<UserManualUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val chapters = userManualRepository.loadChapters()
            _uiState.update {
                it.copy(chapters = chapters, loading = false)
            }
        }
    }
}
