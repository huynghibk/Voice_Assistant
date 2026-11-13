package com.voiceassistant.domain.tts

import javax.inject.Inject
import javax.inject.Singleton

// Splits on clause punctuation (. ! ? … , ; :) — not between words or languages.
@Singleton
class TextSplitter @Inject constructor() {

    fun split(text: String, maxCharsPerChunk: Int = MAX_CHARS_PER_CHUNK): List<String> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val paragraphs = normalized
            .split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        for (paragraph in paragraphs) {
            for (piece in splitByPunctuation(paragraph)) {
                if (piece.length <= maxCharsPerChunk) {
                    chunks += piece
                } else {
                    chunks += softSplitByWords(piece, maxCharsPerChunk)
                }
            }
        }
        return chunks
    }

    private fun splitByPunctuation(paragraph: String): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        var i = 0
        while (i < paragraph.length) {
            val ch = paragraph[i]
            buf.append(ch)
            if (isClauseBoundary(paragraph, i, ch)) {
                val piece = buf.toString().trim()
                if (piece.isNotEmpty()) result += piece
                buf.clear()
            }
            i++
        }
        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }

  private fun isClauseBoundary(text: String, index: Int, ch: Char): Boolean {
        return when (ch) {
            '.', '!', '?', '…' -> true
            ';', ':' -> true
            ',' -> {
                val prevDigit = index > 0 && text[index - 1].isDigit()
                val nextDigit = index + 1 < text.length && text[index + 1].isDigit()
                !(prevDigit && nextDigit)
            }
            else -> false
        }
    }

    private fun softSplitByWords(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val window = text.substring(start, end)
                val breakAt = window.lastIndexOfAny(charArrayOf(' ', '\n', '—', '-'))
                if (breakAt > 0) {
                    end = start + breakAt + 1
                }
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
        const val MAX_CHARS_PER_CHUNK = 500
    }
}
