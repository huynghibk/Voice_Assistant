package com.voiceassistant.engine.translation

import android.icu.text.Transliterator

/**
 * Light cleanup for ML Kit output (literal / repetitive wording).
 */
object OfflineTranslationPolisher {

    private val duplicateWord = Regex("""\b(\p{L}+)\s+\1\b""", RegexOption.IGNORE_CASE)
    private val duplicateChunk = Regex("""(.{4,60}?)\s+\1""")

    fun polish(text: String, targetLang: String): String {
        if (text.isBlank()) return text
        var out = text.trim()
        out = fixMisrenderedTerms(out)
        repeat(3) {
            val next = duplicateWord.replace(out, "$1")
            if (next == out) return@repeat
            out = next
        }
        out = duplicateChunk.replace(out, "$1")
        if (LanguageCodes.isTraditionalChinese(targetLang)) {
            out = toTraditionalChinese(out)
        }
        return out.trim()
    }

    private fun fixMisrenderedTerms(text: String): String = text
        .replace(Regex("""\bMFG\s+Party\b""", RegexOption.IGNORE_CASE), "MFG")
        .replace(Regex("""MFG党"""), "MFG")
        .replace(Regex("""\bVendor\s+World\b""", RegexOption.IGNORE_CASE), "vendor World")
        .replace(Regex("""\bthe\s+Run\s+Control\b""", RegexOption.IGNORE_CASE), "the control run")
        .replace(Regex("""运行运行"""), "运行")
        .replace(Regex("""運行運行"""), "運行")
        .replace(Regex("""実行制御を実行"""), "制御を実行")
        .replace(Regex("""\bI have\b""", RegexOption.IGNORE_CASE), "ME has")
        .replace(Regex("""\bCurrently I\b""", RegexOption.IGNORE_CASE), "Currently ME")

    private fun toTraditionalChinese(text: String): String = runCatching {
        val id = "Simplified-Traditional"
        val tr = Transliterator.getInstance(id)
        tr?.transliterate(text) ?: text
    }.getOrElse { text }
}
