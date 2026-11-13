package com.iptv.app

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object VodMetadata {
    fun durationSeconds(value: String): Long? {
        val parts = value.trim().split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3 || parts.any { it < 0 } || parts.drop(1).any { it >= 60 }) return null
        return parts.fold(0L) { total, part -> (total * 60 + part).coerceAtMost(7 * 24 * 3600) }.takeIf { it > 0 }
    }
    fun trailerUrl(value: String): String? {
        val input = value.trim()
        if (input.matches(Regex("[A-Za-z0-9_-]{11}"))) return "https://www.youtube.com/watch?v=$input"
        val url = input.toHttpUrlOrNull() ?: return null
        return url.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
    }
}
enum class VodSort(val label: String) { TITLE("Title A–Z"), RELEASE("Release date"), ADDED("Date added"), RATING("Provider rating") }
data class VodFilter(val query: String = "", val category: String = "", val year: String = "", val language: String = "", val genre: String = "", val ratedOnly: Boolean = false, val sort: VodSort = VodSort.ADDED)
data class VodPage(val items: List<CatalogItem>, val total: Int, val offset: Int)
data class VodFacets(val years: List<String> = emptyList(), val languages: List<String> = emptyList(), val hasRatings: Boolean = false, val genres: List<String> = emptyList())
data class SeriesContinuation(val episode: Episode, val resume: Boolean, val next: Boolean)
object SeriesProgress {
    fun continuation(seriesId: String, episodes: List<Episode>, history: List<WatchRecord>, watched: Set<String> = emptySet()): SeriesContinuation? {
        val ordered = episodes.sortedWith(compareBy({ it.season }, { it.number }))
        if (ordered.isEmpty()) return null
        val latest = history.firstOrNull { it.item.parentSeriesId == seriesId && ordered.any { ep -> ep.item.id == it.item.id } }
        if (latest == null) return ordered.firstOrNull { it.item.key !in watched }?.let { SeriesContinuation(it, false, false) }
        val index = ordered.indexOfFirst { it.item.id == latest.item.id }
        if (latest.completed) return ordered.drop(index + 1).firstOrNull { it.item.key !in watched }?.let { SeriesContinuation(it, false, true) }
        return SeriesContinuation(ordered[index], latest.position > 0, false)
    }
}
interface RecommendationEngine {
    fun recommend(candidates: List<CatalogItem>, history: List<WatchRecord>, favorites: List<CatalogItem>, limit: Int = 12): List<CatalogItem>
}
class LocalRecommendations : RecommendationEngine {
    override fun recommend(candidates: List<CatalogItem>, history: List<WatchRecord>, favorites: List<CatalogItem>, limit: Int): List<CatalogItem> {
        val signals = history.take(30).map { it.item } + favorites
        if (signals.isEmpty()) return emptyList()
        val genres = signals.flatMap { it.genre.orEmpty().lowercase().split(',', '/', '|').map(String::trim) }.filter { it.isNotBlank() }.toSet()
        val categories = signals.map { it.kind to it.categoryId }.filter { it.second.isNotBlank() }.toSet()
        val known = signals.map { it.key }.toSet()
        return candidates.asSequence().filter { it.key !in known }.map { item ->
            val overlap = item.genre.orEmpty().lowercase().split(',', '/', '|').map(String::trim).count { it in genres }
            item to (overlap * 2 + if ((item.kind to item.categoryId) in categories) 1 else 0)
        }.filter { it.second > 0 }.sortedWith(compareByDescending<Pair<CatalogItem, Int>> { it.second }.thenBy { it.first.name })
            .take(limit.coerceIn(0, 30)).map { it.first }.toList()
    }
}
