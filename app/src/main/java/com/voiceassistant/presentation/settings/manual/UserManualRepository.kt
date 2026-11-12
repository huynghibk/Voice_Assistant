package com.voiceassistant.presentation.settings.manual

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ManualChapter(
    val index: Int,
    val title: String,
    val body: String
)

@Singleton
class UserManualRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cached: List<ManualChapter>? = null

    fun loadChapters(): List<ManualChapter> {
        cached?.let { return it }
        val raw = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            FALLBACK_MANUAL
        }
        val chapters = parseMarkdownChapters(raw)
        cached = chapters
        return chapters
    }

    private fun parseMarkdownChapters(markdown: String): List<ManualChapter> {
        val lines = markdown.replace("\r\n", "\n").lines()
        val chapters = mutableListOf<ManualChapter>()
        var currentTitle: String? = null
        val body = StringBuilder()

        fun flush() {
            val title = currentTitle ?: return
            val text = body.toString().trim()
            if (title.isNotBlank()) {
                chapters += ManualChapter(
                    index = chapters.size,
                    title = title.trim(),
                    body = text.ifBlank { "(Trống)" }
                )
            }
            body.clear()
        }

        // Intro before first ## becomes "Giới thiệu"
        currentTitle = "Giới thiệu"
        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    flush()
                    currentTitle = line.removePrefix("## ").trim()
                        .replace(Regex("^\\d+\\.\\s*"), "") // drop "1. "
                }
                line.startsWith("# ") -> {
                    // Document title — keep in intro
                    if (currentTitle == "Giới thiệu" && body.isEmpty()) {
                        body.appendLine(line.removePrefix("# ").trim())
                        body.appendLine()
                    }
                }
                else -> body.appendLine(line)
            }
        }
        flush()
        return chapters.ifEmpty {
            listOf(
                ManualChapter(0, "Hướng dẫn", FALLBACK_MANUAL)
            )
        }
    }

    companion object {
        private const val ASSET_PATH = "docs/USER_MANUAL.md"

        private val FALLBACK_MANUAL = """
            VoiceAssistant giúp bạn nghe văn bản (TTS), ghi âm thành chữ,
            dịch / quét camera, clone giọng, soạn dự án và tìm kiếm.
            
            Mở các tab: TTS · Dịch · Studio · Voices · Search · Cài đặt để bắt đầu.
        """.trimIndent()
    }
}
