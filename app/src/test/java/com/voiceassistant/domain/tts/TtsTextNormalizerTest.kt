package com.voiceassistant.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsTextNormalizerTest {

    private val normalizer = TtsTextNormalizer()

    @Test
    fun collapsesNbspAndExtraSpaces() {
        val out = normalizer.normalize("Xin\u00A0chào   bạn")
        assertEquals("Xin chào bạn", out)
    }
}
