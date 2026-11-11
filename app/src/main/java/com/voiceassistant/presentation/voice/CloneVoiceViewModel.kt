package com.voiceassistant.presentation.voice

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.usecase.CloneVoiceUseCase
import com.voiceassistant.engine.audio.StudioRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class CloneUiState(
    val name: String = "Giọng của tôi",
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val peakLevel: Float = 0f,
    val status: String = "Ghi 1–5 giây mẫu giọng rõ ràng.",
    val lastVoiceId: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class CloneVoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloneVoiceUseCase: CloneVoiceUseCase,
    private val studioRecorder: StudioRecorder
) : ViewModel() {

    private val _ui = MutableStateFlow(CloneUiState())
    val ui: StateFlow<CloneUiState> = _ui.asStateFlow()

    private var recordJob: Job? = null

    init {
        viewModelScope.launch {
            studioRecorder.state.collect { st ->
                _ui.update {
                    it.copy(
                        isRecording = st.isRecording,
                        elapsedMs = st.elapsedMs,
                        peakLevel = st.peakLevel
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) = _ui.update { it.copy(name = v) }

    fun startRecording() {
        if (!studioRecorder.hasMicPermission()) {
            _ui.update { it.copy(status = "Cần quyền micro để ghi âm.") }
            return
        }
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            _ui.update { it.copy(busy = true, status = "Đang ghi…") }
            try {
                val sample = studioRecorder.recordToWav(maxDurationMs = 5_000L)
                _ui.update { it.copy(status = "Đang trích xuất embedding…") }
                val voice = cloneVoiceUseCase.cloneFromSample(sample, _ui.value.name)
                _ui.update {
                    it.copy(
                        busy = false,
                        lastVoiceId = voice.id,
                        status = "Đã clone: ${voice.name}"
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(busy = false, isRecording = false, status = e.message ?: "Ghi âm thất bại")
                }
            }
        }
    }

    fun stopRecording() {
        cloneVoiceUseCase.stopRecording()
    }

    fun importSample(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, status = "Đang nhập mẫu…") }
            try {
                val dest = File(context.filesDir, "voice_samples/import_${System.currentTimeMillis()}.wav")
                dest.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: error("Không đọc được file")
                val voice = cloneVoiceUseCase.cloneFromSample(dest, _ui.value.name)
                _ui.update {
                    it.copy(busy = false, lastVoiceId = voice.id, status = "Đã clone từ file: ${voice.name}")
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, status = e.message ?: "Import thất bại") }
            }
        }
    }
}
