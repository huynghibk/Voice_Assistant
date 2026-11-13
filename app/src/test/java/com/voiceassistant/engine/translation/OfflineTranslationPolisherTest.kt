package com.voiceassistant.engine.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineTranslationPolisherTest {

    @Test
    fun fixesMfgPartyAndDuplicateRun() {
        val raw = "MFG Party, run run control"
        val out = OfflineTranslationPolisher.polish(raw, "en")
        assertFalse(out.contains("Party"))
        assertEquals("MFG, run control", out)
    }

    @Test
    fun fixesJapaneseDuplicateControl() {
        val raw = "実行制御を実行します"
        val out = OfflineTranslationPolisher.polish(raw, "ja")
        assertEquals("制御を実行します", out)
    }
}
