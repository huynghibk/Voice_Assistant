package com.voiceassistant.domain.tts

import javax.inject.Inject
import javax.inject.Singleton

// Splits mixed vi / en / zh text into contiguous language runs for per-model TTS routing.
@Singleton
class TtsLanguageSegmenter @Inject constructor() {

    private val vietnameseMarks = Regex("""[ăâđêôơưĂÂĐÊÔƠƯàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]""")
    private val han = Regex("""\p{Script=Han}""")
    private val asciiWord = Regex("""^[A-Za-z0-9][A-Za-z0-9'’\-]*$""")

    fun segment(text: String): List<TtsLanguageSegment> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val result = mutableListOf<TtsLanguageSegment>()
        var runLang: TtsLanguage? = null
        val run = StringBuilder()

        fun flush() {
            if (run.isEmpty()) return
            result += TtsLanguageSegment(language = runLang ?: TtsLanguage.OTHER, text = run.toString())
            run.clear()
        }

        for (token in tokenize(trimmed)) {
            if (token.isBlank()) {
                run.append(token)
                continue
            }
            val lang = classifyToken(token)
            if (runLang == null) {
                runLang = lang
            } else if (lang != TtsLanguage.OTHER && lang != runLang) {
                flush()
                runLang = lang
            } else if (runLang == TtsLanguage.OTHER && lang != TtsLanguage.OTHER) {
                runLang = lang
            }
            run.append(token)
        }
        flush()
        return mergeAdjacent(result)
    }

    private fun tokenize(text: String): List<String> {
        val pattern = Regex("""(\p{L}+|\p{N}+|[^\p{L}\p{N}\s]+|\s+)""")
        return pattern.findAll(text).map { it.value }.toList()
    }

    private fun classifyToken(token: String): TtsLanguage {
        if (han.containsMatchIn(token)) return TtsLanguage.CHINESE
        if (vietnameseMarks.containsMatchIn(token)) return TtsLanguage.VIETNAMESE
        if (asciiWord.matches(token)) return TtsLanguage.ENGLISH
        if (token.any { it.isLetter() }) return TtsLanguage.VIETNAMESE
        return TtsLanguage.OTHER
    }

    private fun mergeAdjacent(segments: List<TtsLanguageSegment>): List<TtsLanguageSegment> {
        if (segments.isEmpty()) return segments
        val out = mutableListOf<TtsLanguageSegment>()
        var current = segments.first()
        for (i in 1 until segments.size) {
            val next = segments[i]
            if (next.language == current.language) {
                current = current.copy(text = current.text + next.text)
            } else {
                out += current
                current = next
            }
        }
        out += current
        return out.filter { it.text.isNotBlank() }
    }
}
