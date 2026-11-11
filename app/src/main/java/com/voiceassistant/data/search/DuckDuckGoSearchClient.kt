package com.voiceassistant.data.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String? = null
)

/**
 * Lightweight free Instant Answer client (no API key).
 * Falls back gracefully when the network or API is unavailable.
 */
@Singleton
class DuckDuckGoSearchClient @Inject constructor() {

    suspend fun search(query: String, limit: Int = 5): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val endpoint =
                "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VoiceAssistant/1.0")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().readText()
                parse(body, limit)
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(json: String, limit: Int): List<WebSearchResult> {
        val root = JSONObject(json)
        val results = mutableListOf<WebSearchResult>()

        val abstractText = root.optString("AbstractText").orEmpty()
        val abstractUrl = root.optString("AbstractURL").takeIf { it.isNotBlank() }
        val heading = root.optString("Heading").orEmpty()
        if (abstractText.isNotBlank()) {
            results += WebSearchResult(
                title = heading.ifBlank { "Tóm tắt" },
                snippet = abstractText,
                url = abstractUrl
            )
        }

        val answer = root.optString("Answer").orEmpty()
        if (answer.isNotBlank()) {
            results += WebSearchResult(title = "Answer", snippet = answer)
        }

        val related = root.optJSONArray("RelatedTopics") ?: return results.take(limit)
        for (i in 0 until related.length()) {
            if (results.size >= limit) break
            val item = related.optJSONObject(i) ?: continue
            if (item.has("Topics")) {
                val nested = item.optJSONArray("Topics") ?: continue
                for (j in 0 until nested.length()) {
                    if (results.size >= limit) break
                    nested.optJSONObject(j)?.let { addTopic(it, results) }
                }
            } else {
                addTopic(item, results)
            }
        }
        return results.take(limit)
    }

    private fun addTopic(obj: JSONObject, out: MutableList<WebSearchResult>) {
        val text = obj.optString("Text").orEmpty()
        if (text.isBlank()) return
        out += WebSearchResult(
            title = text.substringBefore(" - ").ifBlank { text.take(48) },
            snippet = text,
            url = obj.optString("FirstURL").takeIf { it.isNotBlank() }
        )
    }
}
