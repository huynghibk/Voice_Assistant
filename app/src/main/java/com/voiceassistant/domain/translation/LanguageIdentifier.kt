package com.voiceassistant.domain.translation

/**
 * Source-language auto-detect. Returns app language id (en, vi, ja, …).
 * Unknown / und → fallback `"en"`.
 */
interface LanguageIdentifier {
    suspend fun identify(text: String): String

    companion object {
        const val AUTO = "auto"
        const val FALLBACK = "en"
    }
}
