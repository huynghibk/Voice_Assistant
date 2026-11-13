package com.voiceassistant.engine.translation

import com.voiceassistant.data.network.NetworkMonitor
import com.voiceassistant.domain.repository.TranslationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart router:
 * - Online → prefer Gemini
 * - Offline / Gemini failure → ML Kit offline fallback
 */
@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val geminiOnlineEngine: GeminiOnlineEngine,
    private val mlKitOfflineEngine: MlKitOfflineEngine,
    private val networkMonitor: NetworkMonitor
) : TranslationRepository {

    override fun supportsOnlineAi(): Boolean =
        networkMonitor.isOnline() && geminiOnlineEngine.hasApiKey()

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> {
        if (text.isBlank()) return Result.success(text)

        if (networkMonitor.isOnline()) {
            val online = geminiOnlineEngine.translate(text, sourceLang, targetLang)
            if (online.isSuccess) {
                return online.map { TranslationCasePreserver.restoreCasing(text, it) }
            }
        }

        val offline = mlKitOfflineEngine.translate(text, sourceLang, targetLang)
        if (offline.isSuccess) {
            return offline.map { TranslationCasePreserver.restoreCasing(text, it) }
        }

        return Result.failure(
            offline.exceptionOrNull()
                ?: IllegalStateException("Không thể dịch (online và offline đều thất bại).")
        )
    }
}
