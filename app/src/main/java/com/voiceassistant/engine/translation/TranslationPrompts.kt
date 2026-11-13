package com.voiceassistant.engine.translation

object TranslationPrompts {

    fun geminiPrompt(text: String, sourceLang: String, targetLang: String): String {
        val sourceName = LanguageCodes.displayName(sourceLang)
        val targetName = LanguageCodes.displayName(targetLang)
        val targetHints = targetStyleHints(targetLang)
        return buildString {
            appendLine("You are an expert translator for factory, engineering, and workplace messages.")
            appendLine("Translate from $sourceName to $targetName.")
            appendLine()
            appendLine("Rules:")
            appendLine("- Return ONLY the translation. No quotes, labels, markdown, or explanations.")
            appendLine("- Keep Latin acronyms and codes as-is when they are names or IDs: ME, MFG, OK, Ver 01, vendor names, part numbers, sizes.")
            appendLine("- Preserve the exact upper/lowercase of Latin words and codes as they appear in the source (ME stays ME, OK stays OK, not \"me\" or \"ok\").")
            appendLine("- ME is a department/team name, never the pronoun \"I\" or \"me\".")
            appendLine("- \"bên\" means a side/department (e.g. MFG team), not \"party\".")
            appendLine("- Keep embedded English technical terms natural in the target language (e.g. control run, mold block) or translate them in standard industry wording—do not garble word order.")
            appendLine("- Professional tone suitable for email or work chat.")
            appendLine("- Do not repeat phrases or words.")
            append(targetHints)
            appendLine()
            appendLine("Source text:")
            append(text)
        }
    }

    private fun targetStyleHints(targetLang: String): String = when {
        LanguageCodes.isTraditionalChinese(targetLang) ->
            "- Use Traditional Chinese characters (Taiwan/Hong Kong written style).\n"
        LanguageCodes.isSimplifiedChinese(targetLang) ->
            "- Use Simplified Chinese (mainland China).\n"
        targetLang.trim().lowercase().startsWith("ja") ->
            "- Use polite Japanese business style (丁寧語); natural manufacturing terminology.\n"
        targetLang.trim().lowercase().startsWith("en") ->
            "- Use clear international manufacturing English.\n"
        else -> ""
    }
}
