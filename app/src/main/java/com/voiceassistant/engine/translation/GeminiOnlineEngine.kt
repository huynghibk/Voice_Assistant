package com.voiceassistant.engine.translation

import com.google.ai.client.generativeai.GenerativeModel
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
        val model = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = key)
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

            val sourceName = LanguageCodes.displayName(sourceLang)
            val targetName = LanguageCodes.displayName(targetLang)
            val prompt = buildString {
                appendLine("You are a precise translation engine.")
                appendLine("Translate the following text from $sourceName to $targetName.")
                appendLine("Return ONLY the translated text with no quotes, labels, or commentary.")
                appendLine()
                append(text)
            }

            val response = generativeModel.generateContent(prompt)
            val translated = response.text?.trim().orEmpty()
            if (translated.isBlank()) {
                error("Gemini trả về kết quả trống.")
            }
            translated
        }
    }
}
