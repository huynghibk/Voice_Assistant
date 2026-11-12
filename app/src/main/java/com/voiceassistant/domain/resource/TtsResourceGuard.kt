package com.voiceassistant.domain.resource

import com.voiceassistant.domain.model.VoiceModel

/**
 * Soft checks before Play / Translate so missing packs never become a hard crash or "hú" tone.
 * Play always proceeds (System TTS fallback); Translate may prompt to download ML Kit packs.
 */
interface TtsResourceGuard {
    data class PlayAdvice(
        val usingSystemTts: Boolean,
        val message: String? = null,
        val suggestOpenModelManager: Boolean = false
    )

    data class TranslateAdvice(
        val canProceed: Boolean,
        val needsDownload: Boolean,
        val pairLabel: String? = null,
        val sourceCode: String? = null,
        val targetCode: String? = null,
        val estimatedSizeMb: Int = 0,
        val message: String? = null
    )

    suspend fun advisePlay(voice: VoiceModel): PlayAdvice
    suspend fun adviseTranslate(sourceLang: String, targetLang: String): TranslateAdvice
}
