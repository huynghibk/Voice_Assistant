package com.voiceassistant.domain.engine

/**
 * Abstraction for online/offline translation backends (Gemini, ML Kit, …).
 */
interface TranslationEngine {
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String>
}
