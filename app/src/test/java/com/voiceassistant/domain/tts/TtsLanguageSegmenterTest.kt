package com.voiceassistant.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsLanguageSegmenterTest {

    private val segmenter = TtsLanguageSegmenter()

    @Test
    fun splitsMixedViEnZh() {
        val segments = segmenter.segment("Hôm nay học Hello và 你好")
        assertTrue(segments.size >= 2)
        assertTrue(segments.any { it.language == TtsLanguage.VIETNAMESE })
        assertTrue(segments.any { it.language == TtsLanguage.ENGLISH || it.language == TtsLanguage.CHINESE })
    }

    @Test
    fun detectsChineseHan() {
        val segments = segmenter.segment("欢迎")
        assertEquals(1, segments.size)
        assertEquals(TtsLanguage.CHINESE, segments.first().language)
    }

    @Test
    fun vietnameseDiacritics() {
        val segments = segmenter.segment("xin chào")
        assertEquals(TtsLanguage.VIETNAMESE, segments.first().language)
    }

    @Test
    fun mixedViEnKeepsWordsWhole() {
        val text = "Pls support fix issue hàng test pas COX nhưng không scan được trạm BAKIN"
        val segments = segmenter.segment(text)
        assertTrue(segments.any { "test pas COX" in it.text || it.text.contains("test pas") })
        assertFalse(segments.any { it.text == "t" || it.text == "est" })
    }
}
