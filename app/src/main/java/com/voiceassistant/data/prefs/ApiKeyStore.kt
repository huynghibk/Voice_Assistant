package com.voiceassistant.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user-supplied Gemini API key in EncryptedSharedPreferences.
 * Falls back to empty when encryption is unavailable (caller may use BuildConfig).
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences(FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    fun getGeminiApiKey(): String =
        prefs.getString(KEY_GEMINI, "").orEmpty().trim()

    fun setGeminiApiKey(value: String) {
        prefs.edit().putString(KEY_GEMINI, value.trim()).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    fun hasUserKey(): Boolean = getGeminiApiKey().isNotBlank()

    /** Masked hint for UI, e.g. AIzaSy••••••abcd (never returns the full key). */
    fun maskedKeyPreview(): String {
        val key = getGeminiApiKey()
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(6)}••••${key.takeLast(4)}"
    }

    fun getGoogleCloudTtsApiKey(): String =
        prefs.getString(KEY_GOOGLE_CLOUD_TTS, "").orEmpty().trim()

    fun setGoogleCloudTtsApiKey(value: String) {
        prefs.edit().putString(KEY_GOOGLE_CLOUD_TTS, value.trim()).apply()
    }

    fun clearGoogleCloudTtsApiKey() {
        prefs.edit().remove(KEY_GOOGLE_CLOUD_TTS).apply()
    }

    fun hasGoogleCloudTtsKey(): Boolean = getGoogleCloudTtsApiKey().isNotBlank()

    fun maskedGoogleCloudTtsKeyPreview(): String = maskKey(getGoogleCloudTtsApiKey())

    fun getElevenLabsApiKey(): String =
        prefs.getString(KEY_ELEVEN_LABS, "").orEmpty().trim()

    fun setElevenLabsApiKey(value: String) {
        prefs.edit().putString(KEY_ELEVEN_LABS, value.trim()).apply()
    }

    fun clearElevenLabsApiKey() {
        prefs.edit().remove(KEY_ELEVEN_LABS).apply()
    }

    fun hasElevenLabsKey(): Boolean = getElevenLabsApiKey().isNotBlank()

    fun maskedElevenLabsKeyPreview(): String = maskKey(getElevenLabsApiKey())

    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(6)}••••${key.takeLast(4)}"
    }

    companion object {
        private const val FILE_NAME = "va_secure_prefs"
        private const val FILE_NAME_FALLBACK = "va_api_key_fallback"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GOOGLE_CLOUD_TTS = "google_cloud_tts_api_key"
        private const val KEY_ELEVEN_LABS = "elevenlabs_api_key"
    }
}
