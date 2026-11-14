package com.voiceassistant.domain.repository

/**
 * Smart router: prefers online Gemini when networked, falls back to ML Kit offline.
 */
interface TranslationRepository {
    fun supportsOnlineAi(): Boolean

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        onlineOnly: Boolean = false
    ): Result<String>
}
