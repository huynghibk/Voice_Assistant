package com.voiceassistant.engine.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationCasePreserverTest {

    @Test
    fun restoresUppercaseAcronyms() {
        val source =
            "Hiện tại ME đã bàn giao 89 sets mold block OK size 10 cho bên MFG"
        val translated =
            "Currently me handed over 89 sets mold block ok size 10 for MFG"
        val out = TranslationCasePreserver.restoreCasing(source, translated)
        assertEquals(
            "Currently ME handed over 89 sets mold block OK size 10 for MFG",
            out
        )
    }
}
