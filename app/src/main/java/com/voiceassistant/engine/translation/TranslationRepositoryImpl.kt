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

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> {
        if (text.isBlank()) return Result.success(text)

        if (networkMonitor.isOnline()) {
            val online = geminiOnlineEngine.translate(text, sourceLang, targetLang)
            if (online.isSuccess) return online
            // Fall through to offline on any Gemini error (quota, missing key, …)
        }

        val offline = mlKitOfflineEngine.translate(text, sourceLang, targetLang)
        if (offline.isSuccess) return offline

        return Result.failure(
            offline.exceptionOrNull()
                ?: IllegalStateException("Không thể dịch (online và offline đều thất bại).")
        )
    }
}
