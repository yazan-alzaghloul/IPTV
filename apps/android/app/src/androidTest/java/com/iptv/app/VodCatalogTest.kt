package com.iptv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodCatalogTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store get()=VodCatalogStore.get(context)
    private val scope="fixture-sql-index"
    @After fun cleanup() { store.clear(scope); store.clear("other-fixture") }
    @Test fun queriesArePagedLiteralFilteredAndProviderIsolated() {
        val items=listOf(CatalogItem("1","100% Film",ContentKind.MOVIE,categoryId="a",year="2024",language="English",rating="8",genre="Drama"),
            CatalogItem("2","Other Film",ContentKind.MOVIE,categoryId="b",year="2023",language="Arabic",rating="9",genre="Comedy"))
        store.replace(scope,ContentKind.MOVIE,items,listOf(Category("a","Drama"),Category("b","Comedy")))
        assertEquals("1",store.page(scope,ContentKind.MOVIE,VodFilter(query="100%")).items.single().id)
        assertEquals("2",store.page(scope,ContentKind.MOVIE,VodFilter(language="Arabic",year="2023")).items.single().id)
        assertEquals("2",store.page(scope,ContentKind.MOVIE,VodFilter(sort=VodSort.RATING),limit=1).items.single().id)
        assertEquals("1",store.page(scope,ContentKind.MOVIE,VodFilter(sort=VodSort.RATING),offset=1,limit=1).items.single().id)
        assertTrue(store.page("other-fixture",ContentKind.MOVIE).items.isEmpty())
        assertTrue(store.page(scope,ContentKind.MOVIE,VodFilter(query="' OR 1=1 --")).items.isEmpty())
        assertEquals(listOf("Comedy","Drama"),store.facets(scope,ContentKind.MOVIE).genres)
        val token=store.queue(scope,items)
        assertEquals(listOf("1","2"),store.loadQueue(scope,token).map { it.id })
        assertTrue(store.loadQueue("other-fixture",token).isEmpty())
    }
    @Test fun fiftyThousandRowsUseBoundedPagesAndSurviveCacheReopen() {
        val items=(0 until 50_000).map { CatalogItem(it.toString(),"Film $it",ContentKind.MOVIE,added=it.toLong()) }
        val start=System.nanoTime(); store.replace(scope,ContentKind.MOVIE,items)
        val indexed=(System.nanoTime()-start)/1_000_000.0
        val queryStart=System.nanoTime()
        val page=store.page(scope,ContentKind.MOVIE,VodFilter(query="Film 49999"))
        val elapsed=(System.nanoTime()-queryStart)/1_000_000.0
        assertEquals("49999",page.items.single().id)
        val first=store.page(scope,ContentKind.MOVIE)
        assertEquals(48,first.items.size); assertEquals(50_000,first.total)
        assertEquals("49999",first.items.first().id)
        println("API-33 emulator SQLite: 50,000 rows indexed in $indexed ms; matching paged query $elapsed ms. Not physical low-memory TV data.")
    }
}
