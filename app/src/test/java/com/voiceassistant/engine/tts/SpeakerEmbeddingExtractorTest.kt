package com.voiceassistant.engine.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SpeakerEmbeddingExtractorTest {

    @Test
    fun embeddingHasFixedDimensionAndUnitNorm() {
        val extractor = SpeakerEmbeddingExtractor()
        val sampleRate = 22_050
        val pcm = ShortArray(sampleRate) { i ->
            (sin(2.0 * Math.PI * 180.0 * i / sampleRate) * 20_000).toInt().toShort()
        }
        val emb = extractor.extractFeatures(pcm, sampleRate)
        assertEquals(SpeakerEmbeddingExtractor.EMBED_DIM, emb.size)
        val norm = emb.fold(0f) { a, v -> a + v * v }
        assertTrue(norm in 0.95f..1.05f)
    }
}
