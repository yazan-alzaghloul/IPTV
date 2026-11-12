package com.iptv.app

import org.junit.Assert.*
import org.junit.Test

class VodLogicTest {
    private class Memory(var percent: Int = 95) : KeyValueStore {
        private val values = mutableMapOf<String,String>()
        override fun completionPercent() = percent
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() = values.clear()
    }
    private val account = Credentials("https://one.example/", "fixture", "fixture-password")
    private fun episode(id: String, season: Int, number: Int, parent: String = "10") = Episode(
        CatalogItem(id, "Episode $number", ContentKind.SERIES, parentSeriesId = parent, parentSeriesName = "Show", seasonNumber = season, episodeNumber = number), season, number)
    @Test fun continuationUsesExactParentAndPersistedEpisodeIdentity() {
        val episodes = listOf(episode("2",2,1), episode("1",1,1))
        val foreign = WatchRecord(episode("1",1,1,"99").item, 42_000, 100_000, 1)
        assertFalse(SeriesProgress.continuation("10", episodes, listOf(foreign))!!.resume)
        val real = foreign.copy(item = episodes[0].item)
        assertEquals("2", SeriesProgress.continuation("10", episodes, listOf(real))!!.episode.item.id)
        assertTrue(SeriesProgress.continuation("10", episodes, listOf(real))!!.resume)
    }
    @Test fun completedEpisodeAdvancesOnlyToActualProviderEpisode() {
        val episodes = listOf(episode("1",1,1), episode("2",2,1))
        val history = listOf(WatchRecord(episodes[0].item, 100_000,100_000,1,true))
        assertEquals("2", SeriesProgress.continuation("10",episodes,history)!!.episode.item.id)
        assertTrue(SeriesProgress.continuation("10",episodes,history)!!.next)
        assertNull(SeriesProgress.continuation("10",episodes,listOf(history[0].copy(item = episodes[1].item))))
    }
    @Test fun manualWatchedFlagsAreDurableBeyondHistoryLimitAndProviderScoped() {
        val library = LocalLibrary(Memory())
        val items=(1..300).map { episode(it.toString(),1,it).item }
        library.setWatched(account,items,true)
        assertEquals(300,library.watched(account).size)
        assertTrue(library.history(account).isEmpty())
        assertTrue(library.watched(account.copy(baseUrl="https://other.example/")).isEmpty())
        library.setWatched(account,listOf(items[0]),false)
        assertFalse(items[0].key in library.watched(account))
    }
    @Test fun explicitWatchedStateCanChooseTheFirstUnwatchedProviderEpisode() {
        val episodes=listOf(episode("1",1,1),episode("2",2,1))
        assertEquals("2",SeriesProgress.continuation("10",episodes,emptyList(),setOf(episodes[0].item.key))!!.episode.item.id)
        assertNull(SeriesProgress.continuation("10",episodes,emptyList(),episodes.map { it.item.key }.toSet()))
    }
    @Test fun savedSeasonRecentSearchAndHistoryRemovalRemainAccountScoped() {
        val library=LocalLibrary(Memory()); val item=episode("1",1,1).item
        library.saveSeason(account,"10",2); assertEquals(2,library.season(account,"10"))
        assertNull(library.season(account.copy(username="other"),"10"))
        repeat(15) { library.saveSearch(account,"Query $it") }; library.saveSearch(account,"Query 14")
        assertEquals(10,library.searches(account).size); assertEquals("Query 14",library.searches(account).first())
        library.savePosition(account,item,40_000,120_000); library.toggle(account,item)
        library.removeHistory(account,item); assertTrue(library.history(account).isEmpty()); assertEquals(0L,library.position(account,item)); assertEquals(1,library.favorites(account).size)
    }
    @Test fun completionThresholdIsConfigurableAndUnknownDurationsNeverAutoComplete() {
        val values=Memory(98); val library=LocalLibrary(values); val item=episode("1",1,1).item
        library.savePosition(account,item,96_000,100_000); assertFalse(library.history(account).single().completed)
        library.savePosition(account,item,99_000,100_000); assertTrue(library.history(account).single().completed)
        library.savePosition(account,item,99_000,0); assertFalse(library.history(account).single().completed)
    }
    @Test fun durationsAndTrailerLinksAreValidatedWithoutInventingUnits() {
        assertEquals(3661L,VodMetadata.durationSeconds("01:01:01")); assertEquals(90L,VodMetadata.durationSeconds("01:30"))
        assertNull(VodMetadata.durationSeconds("90")); assertNull(VodMetadata.durationSeconds("00:99:00"))
        assertNull(VodMetadata.trailerUrl("javascript:alert(1)")); assertNull(VodMetadata.trailerUrl("https://user:pass@example.com/video"))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",VodMetadata.trailerUrl("abcdefghijk"))
    }
    @Test fun recommendationsRequireRealSignalsAndNeverRepeatKnownTitles() {
        val a=CatalogItem("1","One",ContentKind.MOVIE,categoryId="a",genre="Drama")
        val b=a.copy(id="2",name="Two"); val c=a.copy(id="3",name="Other",categoryId="b",genre="Comedy")
        val engine=LocalRecommendations()
        assertTrue(engine.recommend(listOf(a,b,c),emptyList(),emptyList()).isEmpty())
        assertEquals(listOf("2"),engine.recommend(listOf(a,b,c),emptyList(),listOf(a)).map { it.id })
    }
}
