package com.voiceassistant.domain.tts

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits long text into sentence-sized chunks safe for ONNX TTS inference.
 * Prevents OOM / long blocking when users paste ~1000+ words into Quick TTS.
 */
@Singleton
class TextSplitter @Inject constructor() {

    fun split(text: String, maxCharsPerChunk: Int = MAX_CHARS_PER_CHUNK): List<String> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val sentenceRegex = Regex("""[^.!?…]+[.!?…]+|[^.!?…]+$""")
        val sentences = sentenceRegex.findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (sentences.isEmpty()) return softSplit(normalized, maxCharsPerChunk)

        val chunks = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxCharsPerChunk) {
                chunks += sentence
            } else {
                chunks += softSplit(sentence, maxCharsPerChunk)
            }
        }
        return chunks
    }

    private fun softSplit(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val window = text.substring(start, end)
                val breakAt = window.lastIndexOfAny(charArrayOf(' ', '\n', ',', ';', ':', '—', '-'))
                if (breakAt > maxChars / 3) {
                    end = start + breakAt + 1
                }
            }
            result += text.substring(start, end).trim()
            start = end
        }
        return result.filter { it.isNotEmpty() }
    }

    companion object {
        const val MAX_CHARS_PER_CHUNK = 220
    }
}
