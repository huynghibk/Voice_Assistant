package com.voiceassistant.domain.tts

import com.voiceassistant.domain.model.VoiceModel
import javax.inject.Inject
import javax.inject.Singleton

data class TtsPlannedSentence(
    val text: String,
    val voice: VoiceModel,
    val pauseAfterMs: Int = 250
)

@Singleton
class MultiLanguageTtsPlanner @Inject constructor(
    private val normalizer: TtsTextNormalizer,
    private val textSplitter: TextSplitter,
    private val languageSegmenter: TtsLanguageSegmenter
) {

    private val vietnameseMarks = Regex("""[ăâđêôơưĂÂĐÊÔƠƯàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]""")
    private val han = Regex("""\p{Script=Han}""")

    fun plan(text: String, primaryVoice: VoiceModel, voiceCatalog: List<VoiceModel>): List<TtsPlannedSentence> {
        val normalized = normalizer.normalize(text)
        if (normalized.isEmpty()) return emptyList()

        val viVoice = pickVoice(voiceCatalog, primaryVoice, TtsLanguage.VIETNAMESE)
        val enVoice = pickVoice(voiceCatalog, primaryVoice, TtsLanguage.ENGLISH)
        val zhVoice = pickVoice(voiceCatalog, primaryVoice, TtsLanguage.CHINESE)

        val planned = mutableListOf<TtsPlannedSentence>()
        for (chunk in textSplitter.split(normalized)) {
            val runs = languageSegmenter.segment(chunk)
            if (runs.size <= 1) {
                val voice = voiceForChunk(chunk, primaryVoice, viVoice, enVoice, zhVoice)
                planned += TtsPlannedSentence(
                    text = chunk,
                    voice = voice,
                    pauseAfterMs = pauseAfterSentence(chunk)
                )
                continue
            }
            runs.forEachIndexed { index, run ->
                val voice = voiceForLanguage(run.language, primaryVoice, viVoice, enVoice, zhVoice)
                val endOfChunk = index == runs.lastIndex
                planned += TtsPlannedSentence(
                    text = run.text,
                    voice = voice,
                    pauseAfterMs = if (endOfChunk) pauseAfterSentence(chunk) else 80
                )
            }
        }
        return planned
    }

    private fun voiceForLanguage(
        language: TtsLanguage,
        primary: VoiceModel,
        vi: VoiceModel,
        en: VoiceModel,
        zh: VoiceModel
    ): VoiceModel = when (language) {
        TtsLanguage.VIETNAMESE -> vi
        TtsLanguage.ENGLISH -> en
        TtsLanguage.CHINESE -> zh
        TtsLanguage.OTHER -> primary
    }

    private fun voiceForChunk(
        chunk: String,
        primary: VoiceModel,
        vi: VoiceModel,
        en: VoiceModel,
        zh: VoiceModel
    ): VoiceModel {
        when {
            han.containsMatchIn(chunk) -> return zh
            vietnameseMarks.containsMatchIn(chunk) -> return vi
            isMostlyAsciiWords(chunk) -> {
                if (primary.tags.any { it.equals("vi", ignoreCase = true) }) return vi
                return en
            }
            else -> return primary
        }
    }

    private fun isMostlyAsciiWords(text: String): Boolean {
        var latin = 0
        var otherLetters = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            if (ch in 'A'..'Z' || ch in 'a'..'z') latin++ else otherLetters++
        }
        val total = latin + otherLetters
        if (total == 0) return latin > 0
        return latin.toFloat() / total >= 0.85f
    }

    private fun pickVoice(
        catalog: List<VoiceModel>,
        primary: VoiceModel,
        language: TtsLanguage
    ): VoiceModel {
        val tag = when (language) {
            TtsLanguage.VIETNAMESE -> "vi"
            TtsLanguage.CHINESE -> "zh"
            TtsLanguage.ENGLISH -> "en"
            TtsLanguage.OTHER -> ""
        }
        if (primary.tags.any { it.equals(tag, ignoreCase = true) }) {
            return primary
        }
        if (tag.isNotEmpty()) {
            catalog.firstOrNull { vm ->
                vm.tags.any { it.equals(tag, ignoreCase = true) } &&
                    !vm.modelAssetPath.isNullOrBlank()
            }?.let { return it }
        }
        if (language == TtsLanguage.OTHER) {
            return primary
        }
        return when (language) {
            TtsLanguage.ENGLISH -> catalog.firstOrNull {
                it.tags.any { t -> t.equals("en", ignoreCase = true) }
            } ?: primary
            TtsLanguage.CHINESE -> catalog.firstOrNull {
                it.tags.any { t -> t.equals("zh", ignoreCase = true) }
            } ?: catalog.firstOrNull {
                it.tags.any { t -> t.equals("en", ignoreCase = true) }
            } ?: primary
            TtsLanguage.VIETNAMESE -> catalog.firstOrNull {
                it.tags.any { t -> t.equals("vi", ignoreCase = true) }
            } ?: primary
            TtsLanguage.OTHER -> primary
        }
    }

    private fun pauseAfterSentence(sentence: String): Int {
        val last = sentence.trimEnd().lastOrNull() ?: return 120
        return when (last) {
            ',', ';', ':' -> 150
            '?' -> 280
            '!', '。', '！', '？' -> 300
            '.', '…', '\n' -> 250
            else -> 120
        }
    }
}
