package com.voiceassistant.engine.translation

/**
 * Keeps Latin spelling case from the source (ME → ME, not "me") in translated output.
 */
object TranslationCasePreserver {

    private val latinWord = Regex("""[\p{IsLatin}]+[\p{IsLatin}\p{IsDigit}_/-]*""")

    fun restoreCasing(source: String, translated: String): String {
        if (source.isBlank() || translated.isBlank()) return translated
        val originals = collectCasefulTokens(source)
        if (originals.isEmpty()) return translated
        var out = translated
        for (original in originals.sortedByDescending { it.length }) {
            val pattern = original
                .trim()
                .split(Regex("""\s+"""))
                .joinToString("""\s+""") { Regex.escape(it) }
            val regex = Regex("""(?i)\b$pattern\b""")
            out = regex.replace(out, original)
        }
        return out
    }

    private fun collectCasefulTokens(source: String): List<String> {
        val found = linkedSetOf<String>()
        Regex("""\bVer\s*0?\d+\b""", RegexOption.IGNORE_CASE).findAll(source).forEach {
            val raw = it.value
            if (raw.any { ch -> ch.isUpperCase() }) found.add(raw)
        }
        Regex("""\b[\p{IsLatin}]+(?:\s+[\p{IsLatin}]+){0,3}\b""").findAll(source).forEach { match ->
            val raw = match.value.trim()
            if (raw.any { ch -> ch.isUpperCase() }) found.add(raw)
        }
        latinWord.findAll(source).forEach { match ->
            val raw = match.value
            if (raw.length <= 1) return@forEach
            if (raw.any { ch -> ch.isUpperCase() }) found.add(raw)
        }
        return found.toList()
    }
}
