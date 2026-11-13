package com.voiceassistant.domain.tts

enum class TtsLanguage {
    VIETNAMESE,
    CHINESE,
    ENGLISH,
    OTHER
}

data class TtsLanguageSegment(
    val language: TtsLanguage,
    val text: String
)
