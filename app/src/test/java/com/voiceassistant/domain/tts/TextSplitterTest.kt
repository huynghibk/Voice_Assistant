package com.voiceassistant.domain.tts

import org.junit.Assert.assertEquals
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
    fun softSplitsVeryLongSentence() {
        val long = "word ".repeat(100).trim()
        val chunks = splitter.split(long, maxCharsPerChunk = 40)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 45 })
    }

    @Test
    fun emptyTextReturnsEmpty() {
        assertTrue(splitter.split("   ").isEmpty())
    }
}
