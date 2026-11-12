package com.voiceassistant.domain.tts

import javax.inject.Inject
import javax.inject.Singleton

// Splits long text into sentence-sized chunks (period / newline / !?).
// Never splits letter-by-letter or word-by-word for normal sentences.
@Singleton
class TextSplitter @Inject constructor() {

    fun split(text: String, maxCharsPerChunk: Int = MAX_CHARS_PER_CHUNK): List<String> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        // Sentence boundaries only: . ! ? … or newline. Keep full sentences intact.
        val sentenceRegex = Regex("""[^.!?…\n]+[.!?…\n]+|[^.!?…\n]+$""")
        val sentences = sentenceRegex.findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (sentences.isEmpty()) return softSplitByWords(normalized, maxCharsPerChunk)

        val chunks = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxCharsPerChunk) {
                chunks += sentence
            } else {
                chunks += softSplitByWords(sentence, maxCharsPerChunk)
            }
        }
        return chunks
    }

    // Only used for oversized sentences — break on whitespace/punctuation, never per character.
    private fun softSplitByWords(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val window = text.substring(start, end)
                val breakAt = window.lastIndexOfAny(charArrayOf(' ', '\n', ',', ';', ':', '—', '-'))
                if (breakAt > 0) {
                    end = start + breakAt + 1
                }
                // If no whitespace in window, keep extending to next space to avoid letter chunks.
                if (breakAt <= 0) {
                    val nextSpace = text.indexOfAny(charArrayOf(' ', '\n'), startIndex = end)
                    end = if (nextSpace > start) nextSpace + 1 else text.length
                }
            }
            val piece = text.substring(start, end).trim()
            if (piece.isNotEmpty()) result += piece
            start = end
        }
        return result
    }

    companion object {
        // Large enough that typical paragraphs stay as whole sentences.
        const val MAX_CHARS_PER_CHUNK = 500
    }
}
