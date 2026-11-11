package com.voiceassistant.data.importing

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun importText(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri)?.lowercase().orEmpty()
        context.contentResolver.openInputStream(uri)?.use { input ->
            when {
                name.endsWith(".txt") || name.endsWith(".md") -> input.bufferedReader().readText()
                name.endsWith(".docx") -> parseDocx(input)
                name.endsWith(".epub") -> parseEpub(input)
                name.endsWith(".pdf") -> parsePdfBestEffort(input)
                else -> {
                    // sniff zip (docx/epub) vs plain text
                    val buffered = BufferedInputStream(input)
                    buffered.mark(4)
                    val sig = ByteArray(4)
                    val read = buffered.read(sig)
                    buffered.reset()
                    when {
                        read >= 2 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte() -> {
                            // Could be docx or epub — try docx first
                            runCatching { parseDocx(buffered) }
                                .recoverCatching { parseEpub(buffered) }
                                .getOrElse { buffered.bufferedReader().readText() }
                        }
                        read >= 4 && sig[0] == '%'.code.toByte() && sig[1] == 'P'.code.toByte() ->
                            parsePdfBestEffort(buffered)
                        else -> buffered.bufferedReader().readText()
                    }
                }
            }
        }?.trim().orEmpty()
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment
    }

    private fun parseDocx(input: InputStream): String {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return extractXmlText(zip, textTags = setOf("t"))
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("DOCX không hợp lệ (thiếu word/document.xml)")
    }

    private fun parseEpub(input: InputStream): String {
        val htmlParts = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val n = entry.name.lowercase()
                if (!entry.isDirectory && (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm"))) {
                    val raw = zip.bufferedReader().readText()
                    htmlParts += stripHtml(raw)
                }
                entry = zip.nextEntry
            }
        }
        return htmlParts.joinToString("\n\n").ifBlank {
            throw IllegalArgumentException("EPUB không có nội dung đọc được")
        }
    }

    /**
     * Best-effort PDF text extraction without PdfBox:
     * pulls visible literal strings from content streams. Works for simple PDFs.
     */
    private fun parsePdfBestEffort(input: InputStream): String {
        val bytes = input.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        val matches = Regex("""\((?:\\.|[^\\)])*\)\s*Tj|\[(.*?)\]\s*TJ""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
        val out = StringBuilder()
        for (m in matches) {
            val chunk = m.groupValues.getOrNull(1) ?: m.value
            val literals = Regex("""\((?:\\.|[^\\)])*\)""").findAll(chunk)
            for (lit in literals) {
                var s = lit.value.removePrefix("(").removeSuffix(")")
                s = s.replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\t", " ")
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\\\", "\\")
                out.append(s)
            }
            out.append(' ')
        }
        val cleaned = out.toString()
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.length < 8) {
            throw IllegalArgumentException(
                "PDF phức tạp — hãy dùng TXT/DOCX/EPUB hoặc PDF text đơn giản."
            )
        }
        return cleaned
    }

    private fun extractXmlText(input: InputStream, textTags: Set<String>): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        val sb = StringBuilder()
        var event = parser.eventType
        var inText = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> inText = parser.name in textTags
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p" || parser.name == "br") sb.append('\n')
                    inText = false
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun stripHtml(html: String): String =
        html
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s+\n"), "\n")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
}
