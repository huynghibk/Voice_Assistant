package com.voiceassistant.engine.translation

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.voiceassistant.BuildConfig
import com.voiceassistant.data.prefs.ApiKeyStore
import com.voiceassistant.domain.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online translation via Gemini.
 * Resolves key: user EncryptedSharedPreferences → BuildConfig (`local.properties`).
 */
@Singleton
class GeminiOnlineEngine @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : TranslationEngine {

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedModel: GenerativeModel? = null

    private fun resolveKey(): String {
        val user = apiKeyStore.getGeminiApiKey()
        if (user.isNotBlank()) return user
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    private fun modelForCurrentKey(): GenerativeModel? {
        val key = resolveKey()
        if (key.isBlank()) {
            cachedKey = null
            cachedModel = null
            return null
        }
        if (key == cachedKey && cachedModel != null) return cachedModel
        val model = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = key,
            generationConfig = generationConfig {
                temperature = 0.15f
            }
        )
        cachedKey = key
        cachedModel = model
        return model
    }

    fun hasApiKey(): Boolean = resolveKey().isNotBlank()

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) return@runCatching text
            val generativeModel = modelForCurrentKey()
                ?: error("Thiếu Gemini API key. Thêm trong Cài đặt hoặc local.properties.")

            val prompt = TranslationPrompts.geminiPrompt(text, sourceLang, targetLang)

            val response = generativeModel.generateContent(prompt)
            val translated = response.text?.trim().orEmpty()
            if (translated.isBlank()) {
                error("Gemini trả về kết quả trống.")
            }
            translated
        }
    }
}
