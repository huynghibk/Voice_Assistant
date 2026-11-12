package com.voiceassistant.domain.model

import java.util.Locale

/**
 * UI languages aligned with offline translation support.
 * Chinese offers both Simplified and Traditional for the interface.
 */
enum class AppLanguage(
    val tag: String,
    val nativeLabel: String,
    /** ML Kit TranslateLanguage code when translating content (null = UI-only). */
    val translateCode: String
) {
    ENGLISH("en", "English", "en"),
    VIETNAMESE("vi", "Tiếng Việt", "vi"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文", "zh"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文", "zh"),
    JAPANESE("ja", "日本語", "ja"),
    KOREAN("ko", "한국어", "ko"),
    FRENCH("fr", "Français", "fr");

    fun toLocale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return ENGLISH
            val normalized = tag.replace('_', '-')
            return entries.firstOrNull {
                it.tag.equals(normalized, ignoreCase = true)
            } ?: entries.firstOrNull {
                normalized.startsWith(it.tag.substringBefore('-'), ignoreCase = true) &&
                    it.tag.contains('-').not()
            } ?: when {
                normalized.startsWith("zh-Hant", true) ||
                    normalized.startsWith("zh-TW", true) ||
                    normalized.startsWith("zh-HK", true) -> CHINESE_TRADITIONAL
                normalized.startsWith("zh", true) -> CHINESE_SIMPLIFIED
                else -> ENGLISH
            }
        }

        fun fromSystem(): AppLanguage = fromTag(Locale.getDefault().toLanguageTag())
    }
}
