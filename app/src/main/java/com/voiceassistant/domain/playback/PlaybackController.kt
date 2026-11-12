package com.voiceassistant.domain.playback

import com.voiceassistant.domain.model.VoiceModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Central TTS playback contract.
 * Phase 2: owns the play job, sentence index, speed, skip, and feeds MediaSessionService.
 */
data class PlaybackState(
    val sentences: List<String> = emptyList(),
    val currentSentenceIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val speed: Float = 1.0f,
    val title: String = "",
    val statusMessage: String = ""
) {
    val sentenceCount: Int get() = sentences.size
    val currentSentence: String
        get() = sentences.getOrNull(currentSentenceIndex).orEmpty()
}

@Deprecated("Use PlaybackState", ReplaceWith("PlaybackState"))
data class SentenceProgress(
    val sentenceIndex: Int = -1,
    val sentenceCount: Int = 0,
    val sentence: String = "",
    val isPlaying: Boolean = false
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** @deprecated Prefer [state] */
    val sentenceProgress: StateFlow<SentenceProgress>

    fun start(text: String, voice: VoiceModel, title: String = "VoiceAssistant")
    fun pause()
    fun resume()
    fun stop()
    fun skipToNextSentence()
    fun skipToPreviousSentence()
    fun setSpeed(speed: Float)

    fun getCollectedPcm(): ShortArray
    fun getSampleRate(): Int
}
