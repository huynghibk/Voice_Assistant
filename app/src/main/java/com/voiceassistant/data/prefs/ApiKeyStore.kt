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

    companion object {
        private const val FILE_NAME = "va_secure_prefs"
        private const val FILE_NAME_FALLBACK = "va_api_key_fallback"
        private const val KEY_GEMINI = "gemini_api_key"
    }
}
