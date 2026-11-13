package com.voiceassistant.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSplitterTest {

    private val splitter = TextSplitter()

    @Test
    fun keepsThousandsCommaIntact() {
        val chunks = splitter.split("Tổng (12,357pcs) và xong.")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("12,357"))
    }

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
    fun foldsSingleNewlineIntoOneSentence() {
        val chunks = splitter.split("issue hàng test\ntest pas COX nhưng không scan")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("test pas"))
    }

    @Test
    fun splitsParagraphsOnBlankLine() {
        val chunks = splitter.split("Câu một\n\nCâu hai\n\nCâu ba")
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
