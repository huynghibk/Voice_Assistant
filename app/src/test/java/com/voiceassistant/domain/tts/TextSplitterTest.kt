package com.voiceassistant.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSplitterTest {

    private val splitter = TextSplitter()

    @Test
    fun splitsOnSentencePunctuation() {
        val chunks = splitter.split("Xin chào. Bạn khỏe không? Tuyệt quá!")
        assertEquals(3, chunks.size)
        assertTrue(chunks[0].contains("Xin chào"))
        assertTrue(chunks[1].contains("khỏe"))
        assertTrue(chunks[2].contains("Tuyệt"))
    }

    @Test
    fun keepsVietnameseParagraphAsOneSentenceWithoutPeriod() {
        val text = "Xin chào các bạn đây là trợ lý giọng nói đọc tự nhiên"
        val chunks = splitter.split(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun splitsOnNewlinesOnlyAsSentences() {
        val chunks = splitter.split("Câu một\nCâu hai\nCâu ba")
        assertEquals(3, chunks.size)
    }

    @Test
    fun neverSplitsLetterByLetter() {
        val text = "Đây là một câu tiếng Việt khá dài nhưng không có dấu chấm ở giữa nên phải giữ nguyên từ."
        val chunks = splitter.split(text, maxCharsPerChunk = 30)
        assertTrue(chunks.isNotEmpty())
        assertFalse(chunks.any { it.length == 1 && it[0].isLetter() })
        assertTrue(chunks.all { it.length >= 2 || !it[0].isLetter() })
    }

    @Test
    fun softSplitsVeryLongSentence() {
        val long = "word ".repeat(100).trim()
        val chunks = splitter.split(long, maxCharsPerChunk = 40)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.none { it.length == 1 })
    }

    @Test
    fun emptyTextReturnsEmpty() {
        assertTrue(splitter.split("   ").isEmpty())
    }
}
