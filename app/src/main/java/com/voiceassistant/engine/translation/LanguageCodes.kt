package com.voiceassistant.engine.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.voiceassistant.domain.translation.LanguageIdentifier

/**
 * Maps UI / BCP-47 style tags to ML Kit TranslateLanguage codes and display labels.
 */
object LanguageCodes {

    fun isTraditionalChinese(idOrCode: String): Boolean {
        val key = idOrCode.trim().lowercase().replace('_', '-')
        return key == "zh-tw" || key.startsWith("zh-hant") || key.startsWith("zh-hk")
    }

    fun isSimplifiedChinese(idOrCode: String): Boolean {
        val key = idOrCode.trim().lowercase().replace('_', '-')
        return key.startsWith("zh") && !isTraditionalChinese(idOrCode)
    }

    fun toMlKit(idOrCode: String): String {
        val key = idOrCode.trim().lowercase().replace('_', '-')
        return when {
            key == LanguageIdentifier.AUTO -> TranslateLanguage.ENGLISH
            key == "vi" || key.startsWith("vi-") -> TranslateLanguage.VIETNAMESE
            key == "en" || key.startsWith("en-") -> TranslateLanguage.ENGLISH
            key.startsWith("zh") -> TranslateLanguage.CHINESE
            key == "ja" || key.startsWith("ja-") -> TranslateLanguage.JAPANESE
            key == "ko" || key.startsWith("ko-") -> TranslateLanguage.KOREAN
            key == "fr" || key.startsWith("fr-") -> TranslateLanguage.FRENCH
            else -> idOrCode
        }
    }

    fun displayName(idOrCode: String): String {
        val key = idOrCode.trim().lowercase().replace('_', '-')
        return when {
            key == LanguageIdentifier.AUTO -> "Auto"
            key == "vi" || key.startsWith("vi-") -> "Vietnamese"
            key == "en" || key.startsWith("en-") -> "English"
            key == "zh-tw" || key.startsWith("zh-hant") || key.startsWith("zh-hk") ->
                "Traditional Chinese"
            key.startsWith("zh") -> "Simplified Chinese"
            key == "ja" || key.startsWith("ja-") -> "Japanese"
            key == "ko" || key.startsWith("ko-") -> "Korean"
            key == "fr" || key.startsWith("fr-") -> "French"
            else -> idOrCode
        }
    }

    /** Localized-ish badge label for the detected language. */
    fun nativeLabel(idOrCode: String): String {
        val key = idOrCode.trim().lowercase().replace('_', '-')
        return when {
            key == LanguageIdentifier.AUTO -> "Tự động"
            key == "vi" || key.startsWith("vi-") -> "Tiếng Việt"
            key == "en" || key.startsWith("en-") -> "English"
            key == "zh-tw" || key.startsWith("zh-hant") || key.startsWith("zh-hk") -> "繁體中文"
            key.startsWith("zh") -> "简体中文"
            key == "ja" || key.startsWith("ja-") -> "日本語"
            key == "ko" || key.startsWith("ko-") -> "한국어"
            key == "fr" || key.startsWith("fr-") -> "Français"
            else -> idOrCode
        }
    }

    /**
     * Maps ML Kit Language ID BCP-47 tag → app language id used by Translate UI.
     */
    fun fromLanguageIdTag(tag: String): String {
        val key = tag.trim().lowercase().replace('_', '-')
        if (key.isBlank() || key == "und") return LanguageIdentifier.FALLBACK
        return when {
            key == "vi" || key.startsWith("vi-") -> "vi"
            key == "en" || key.startsWith("en-") -> "en"
            key == "ja" || key.startsWith("ja-") -> "ja"
            key == "ko" || key.startsWith("ko-") -> "ko"
            key == "fr" || key.startsWith("fr-") -> "fr"
            key.startsWith("zh-hant") || key == "zh-tw" || key == "zh-hk" -> "zh-TW"
            key.startsWith("zh") -> "zh-CN"
            else -> LanguageIdentifier.FALLBACK
        }
    }
}
