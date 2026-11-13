package com.voiceassistant.domain.tts

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsTextNormalizer @Inject constructor() {

    fun normalize(raw: String): String {
        var text = raw
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        text = collapseSoftLineBreaks(text)
        text = collapseEllipsis(text)
        text = normalizePunctuationSpacing(text)
        text = text.replace(Regex("""[^\P{Z}\n\p{L}\p{N}\p{M}\p{P}\s]"""), " ")
        text = text.replace(Regex("""\s{2,}"""), " ")
        return text.trim()
    }

    // Email/OCR often inserts single newlines mid-sentence — fold them into spaces; keep paragraph gaps.
    private fun collapseSoftLineBreaks(text: String): String =
        text
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""(?<!\n)\n(?!\n)"""), " ")

    private fun collapseEllipsis(text: String): String =
        text.replace(Regex("""\.{4,}"""), "…")
            .replace(Regex("""…{2,}"""), "…")

    private fun normalizePunctuationSpacing(text: String): String =
        text.replace(Regex("""\s+([,.!?;:])"""), "$1")
            .replace(Regex("""([(\[])"""), "$1 ")
            .replace(Regex("""\s{2,}"""), " ")
}
