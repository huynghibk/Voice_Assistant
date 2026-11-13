package com.voiceassistant.domain.tts

import com.voiceassistant.domain.model.VoiceModel
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiLanguageTtsPlannerTest {

    private val planner = MultiLanguageTtsPlanner(
        TtsTextNormalizer(),
        TextSplitter(),
        TtsLanguageSegmenter()
    )

    private val viPiper = VoiceModel(
        id = "preset_vi_piper_25h",
        name = "Vi",
        tags = listOf("vi"),
        modelAssetPath = "models/piper/vi_VN-25hours_single-low.onnx"
    )

    private val enPiper = VoiceModel(
        id = "preset_en_amy",
        name = "Amy",
        tags = listOf("en"),
        modelAssetPath = "models/piper/en_US-amy-low.onnx"
    )

    @Test
    fun mixedParagraphStaysFewChunksNotDozens() {
        val text =
            "Em nhận thông tin từ Nga IC lượng hàng ring này được trả ra từ Packing line do NG ring VQ (12,357pcs). " +
                "Hiện tại spec cho hàng xước đã nới, đồng nghĩa là ngoài số lượng này ra thì tất cả các hàng ring chuyển vào packing: " +
                "1 là sẽ OK theo spec mới để packing. 2 là out of spec NG failed luôn k cho vào số lượng cần sorting nữa."
        val planned = planner.plan(text, viPiper, listOf(viPiper))
        assertTrue("expected clause-sized chunks, got ${planned.size}", planned.size in 3..12)
        assertTrue(planned.none { it.text.length <= 3 })
        assertTrue(planned.any { it.text.contains("ring chuyển vào packing", ignoreCase = true) })
    }

    @Test
    fun mixedViEnInOneSentenceUsesEnglishVoiceForLatinRuns() {
        val catalog = listOf(viPiper, enPiper)
        val planned = planner.plan("Hôm nay học Hello và test API.", viPiper, catalog)
        assertTrue(planned.any { it.voice.id == enPiper.id && it.text.contains("Hello", ignoreCase = true) })
        assertTrue(planned.any { it.voice.id == viPiper.id })
    }
}
