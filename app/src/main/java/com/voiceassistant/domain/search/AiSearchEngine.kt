package com.voiceassistant.domain.search

import com.voiceassistant.data.local.db.ProjectFtsDao
import com.voiceassistant.data.local.db.SearchHistoryDao
import com.voiceassistant.data.local.db.SearchHistoryEntity
import com.voiceassistant.data.network.NetworkMonitor
import com.voiceassistant.data.search.DuckDuckGoSearchClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SearchAnswer(
    val prompt: String,
    val response: String,
    val source: String, // local | web | hybrid | offline_fallback
    val id: String = UUID.randomUUID().toString()
)

/**
 * Prompt → local FTS / web API → textual context for TTS ("đọc báo/tin tức").
 */
@Singleton
class AiSearchEngine @Inject constructor(
    private val projectFtsDao: ProjectFtsDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val webClient: DuckDuckGoSearchClient,
    private val networkMonitor: NetworkMonitor
) {

    suspend fun search(prompt: String, preferWeb: Boolean = true): SearchAnswer {
        val q = prompt.trim()
        require(q.isNotEmpty()) { "Prompt trống" }

        val local = searchLocal(q)
        val online = if (preferWeb && networkMonitor.isOnline()) {
            runCatching { searchWeb(q) }.getOrNull()
        } else null

        val answer = when {
            online != null && local != null -> SearchAnswer(
                prompt = q,
                response = buildString {
                    appendLine("【Kết quả web】")
                    appendLine(online)
                    appendLine()
                    appendLine("【Trong dự án của bạn】")
                    append(local)
                },
                source = "hybrid"
            )
            online != null -> SearchAnswer(prompt = q, response = online, source = "web")
            local != null -> SearchAnswer(
                prompt = q,
                response = if (!networkMonitor.isOnline()) {
                    "Đang offline — kết quả nội bộ:\n\n$local"
                } else {
                    local
                },
                source = if (networkMonitor.isOnline()) "local" else "offline_fallback"
            )
            else -> SearchAnswer(
                prompt = q,
                response = if (networkMonitor.isOnline()) {
                    "Không tìm thấy kết quả phù hợp cho: \"$q\""
                } else {
                    "Không có mạng và không có dự án nội bộ khớp với: \"$q\""
                },
                source = "local"
            )
        }

        searchHistoryDao.upsert(
            SearchHistoryEntity(
                id = answer.id,
                prompt = answer.prompt,
                response = answer.response,
                source = answer.source,
                timestamp = System.currentTimeMillis()
            )
        )
        return answer
    }

    private suspend fun searchLocal(query: String): String? {
        val ftsQuery = toFtsQuery(query)
        val hits = runCatching { projectFtsDao.search(ftsQuery, limit = 8) }.getOrDefault(emptyList())
        if (hits.isEmpty()) {
            // Fallback LIKE via MATCH failure / short tokens
            return null
        }
        return hits.joinToString("\n\n") { hit ->
            val snippet = hit.body.replace("\n", " ").take(320)
            "• ${hit.title}: $snippet"
        }
    }

    private suspend fun searchWeb(query: String): String? {
        val results = webClient.search(query, limit = 5)
        if (results.isEmpty()) return null
        return results.joinToString("\n\n") { r ->
            buildString {
                append("• ${r.title}")
                append("\n  ${r.snippet.take(400)}")
                r.url?.let { append("\n  $it") }
            }
        }
    }

    /** Escape user text into a safe FTS MATCH expression (OR tokens). */
    private fun toFtsQuery(raw: String): String {
        val tokens = raw
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[\"'*]"), "") }
            .filter { it.length >= 2 }
            .take(8)
        if (tokens.isEmpty()) return "\"${raw.take(40).replace("\"", "")}\""
        return tokens.joinToString(" OR ") { "$it*" }
    }
}
