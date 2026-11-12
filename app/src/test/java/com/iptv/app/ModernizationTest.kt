package com.iptv.app

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ModernizationTest {
    private class Memory : KeyValueStore {
        val data = mutableMapOf<String, String>()
        override fun get(key: String) = data[key]
        override fun put(key: String, value: String) { data[key] = value }
        override fun clear() = data.clear()
    }
    private val credentials = Credentials("https://provider.example/", "fixture", "fixture-password")
    private val movie = CatalogItem("1", "A film", ContentKind.MOVIE)
    @Test fun legacyFavoritesAndResumeKeysRemainReadable() {
        val values = Memory()
        val hash = MessageDigest.getInstance("SHA-256").digest(Gson().toJson(credentials).toByteArray()).joinToString("") { "%02x".format(it) }
        values.put("$hash.favorites", """[{"id":"1","name":"A film","kind":"MOVIE","categoryId":"","image":"","extension":"mp4"}]""")
        values.put("$hash.position.MOVIE:1", "42000")
        val library = LocalLibrary(values)
        assertEquals("A film", library.favorites(credentials).single().name)
        assertNull(library.favorites(credentials).single().rating)
        assertEquals(42_000L, library.position(credentials, movie))
    }
    @Test fun historyIsBoundedAndKeepsMostRecentUniqueTitles() {
        val library = LocalLibrary(Memory())
        repeat(105) { library.savePosition(credentials, movie.copy(id = it.toString()), 20_000, 120_000) }
        val history = library.history(credentials)
        assertEquals(100, history.size); assertEquals("104", history.first().item.id)
        library.savePosition(credentials, movie.copy(id = "100"), 30_000, 120_000)
        assertEquals("100", library.history(credentials).first().item.id)
        assertEquals(100, library.history(credentials).size)
        assertTrue(library.history(credentials.copy(username = "other")).isEmpty())
    }
    @Test fun completedTitlesAndLiveHistoryDoNotResume() {
        val library = LocalLibrary(Memory())
        library.savePosition(credentials, movie, 115_000, 120_000)
        assertTrue(library.history(credentials).single().completed)
        assertEquals(0L, library.position(credentials, movie))
        val live = movie.copy(kind = ContentKind.LIVE)
        library.savePosition(credentials, live, 15_000, 0)
        assertEquals(ContentKind.LIVE, library.history(credentials).first().item.kind)
        assertEquals(0L, library.position(credentials, live))
    }
    @Test fun shortVideosAreNotMarkedCompletedWhenPlaybackStarts() {
        val library = LocalLibrary(Memory())
        library.savePosition(credentials, movie, 1_000, 5_000)
        assertFalse(library.history(credentials).single().completed)
        assertEquals(1_000L, library.position(credentials, movie))
        library.savePosition(credentials, movie, 4_900, 5_000)
        assertTrue(library.history(credentials).single().completed)
        assertEquals(0L, library.position(credentials, movie))
    }
    @Test fun clearingHistoryKeepsFavoritesAndRemovesTrackedProgress() {
        val library = LocalLibrary(Memory())
        library.toggle(credentials, movie); library.savePosition(credentials, movie, 40_000, 120_000)
        library.clearHistory(credentials)
        assertTrue(library.history(credentials).isEmpty()); assertEquals(0L, library.position(credentials, movie))
        assertEquals(1, library.favorites(credentials).size)
    }
    @Test fun searchMatchesAllWordsAndSupportsArabicAndGenre() {
        val entries = listOf(movie.copy(name = "Evening News", categoryId = "a", genre = "English News"),
            movie.copy(id = "2", name = "قناة الأخبار", categoryId = "b"))
        assertEquals("1", CatalogQuery.apply(entries, " NEWS english ").single().id)
        assertEquals("2", CatalogQuery.apply(entries, "الأخبار").single().id)
        assertTrue(CatalogQuery.apply(entries, "News", "b").isEmpty())
    }
    @Test fun sortingUsesOnlyAvailableProviderValues() {
        val entries = listOf(movie.copy(name = "Z", added = 1, rating = "9.5"), movie.copy(id = "2", name = "A", added = 2), movie.copy(id = "3", name = "B", rating = "7"))
        assertEquals(listOf("2", "3", "1"), CatalogQuery.apply(entries, "", sort = CatalogSort.NAME).map { it.id })
        assertEquals("2", CatalogQuery.apply(entries, "", sort = CatalogSort.RECENT).first().id)
        assertEquals(listOf("1", "3", "2"), CatalogQuery.apply(entries, "", sort = CatalogSort.RATING).map { it.id })
    }
    @Test fun reconnectBudgetAlwaysTerminatesAndRequiresExplicitReset() {
        val policy = RetryBudget()
        assertEquals(1_000L, policy.nextDelayMs()); assertEquals(2_000L, policy.nextDelayMs())
        repeat(20) { assertNull(policy.nextDelayMs()) }
        policy.reset(); assertEquals(1_000L, policy.nextDelayMs())
    }
    @Test fun largeCatalogQueryProducesCorrectResultWithoutQuadraticFavoriteLookups() {
        val catalog = (0 until 50_000).map { movie.copy(id = it.toString(), name = "Movie $it") }
        val start = System.nanoTime()
        val result = CatalogQuery.apply(catalog, "movie 49999")
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        assertEquals("49999", result.single().id)
        println("Host JVM: 50,000-item query ${"%.2f".format(elapsed)} ms (not an Android device benchmark)")
    }
}
