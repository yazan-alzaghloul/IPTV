package com.iptv.app

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private class MemoryValues : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() = values.clear()
    }
    private val values = MemoryValues()
    private val library: LibraryRepository = LocalLibrary(values)
    private val account = Credentials("https://example.com/", "user", "secret")
    private val movie = CatalogItem("1", "Film", ContentKind.MOVIE)
    @Test fun favoritesPersistAndToggleWithoutDuplicates() {
        library.toggle(account, movie)
        assertEquals(listOf(movie), LocalLibrary(values).favorites(account))
        library.toggle(account, movie); assertTrue(library.favorites(account).isEmpty())
    }
    @Test fun accountsAndContentKindsHaveSeparateState() {
        val other = account.copy(username = "other")
        library.toggle(account, movie)
        library.toggle(account, movie.copy(kind = ContentKind.LIVE))
        assertEquals(2, library.favorites(account).size)
        assertTrue(library.favorites(other).isEmpty())
        assertTrue(values.values.keys.none { it.contains("secret") || it.contains("user") })
    }
    @Test fun resumesVodButNeverLiveAndResetsNearEnd() {
        library.savePosition(account, movie, 25_000, 100_000)
        assertEquals(25_000L, library.position(account, movie))
        assertEquals(0L, library.position(account.copy(baseUrl = "https://other.example/"), movie))
        val live = movie.copy(kind = ContentKind.LIVE)
        library.savePosition(account, live, 25_000, 100_000); assertEquals(0L, library.position(account, live))
        library.savePosition(account, movie, 95_000, 100_000); assertEquals(0L, library.position(account, movie))
    }
    @Test fun clearRemovesAllAccountData() {
        library.toggle(account, movie); library.savePosition(account, movie, 20_000, 100_000)
        library.clear()
        assertTrue(values.values.isEmpty()); assertTrue(library.favorites(account).isEmpty()); assertEquals(0L, library.position(account, movie))
    }
}
