package com.iptv.app

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.Base64

class ProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: XtreamRepository
    private lateinit var account: Credentials
    @Before fun setUp() { server = MockWebServer(); server.start(); repository = XtreamRepository(); account = Credentials(server.url("/service/").toString(), "a user", "p/a&ss") }
    @After fun tearDown() { server.shutdown() }
    private fun respond(body: String) { server.enqueue(MockResponse().setBody(body)) }
    @Test fun normalizesHostAndPreservesBasePath() {
        val c = LoginInput.normalize("https://example.com/provider", "8443", "http", " user ", " secret ")
        assertEquals("https://example.com:8443/provider/", c.baseUrl)
        assertEquals("user", c.username); assertEquals(" secret ", c.password)
        assertEquals("http://example.com:8000/", LoginInput.normalize("example.com:8000", "", "http", "u", "p").baseUrl)
    }
    @Test fun invalidLoginInputIsRejected() {
        listOf("0", "65536", "abc", "-1").forEach { port -> assertThrows(IllegalArgumentException::class.java) { LoginInput.normalize("example.com", port, "https", "u", "p") } }
        listOf("", "ftp://example.com", "https://u:p@example.com", "https://example.com/player_api.php", "https://example.com?password=p").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) { LoginInput.normalize(host, "", "https", "u", "p") }
        }
        assertThrows(IllegalArgumentException::class.java) { LoginInput.normalize("example.com", "", "https", "", "p") }
    }
    @Test fun authUsesEncodedCredentialsAndBasePath() = runTest {
        respond("""{"user_info":{"auth":1,"status":"Active","exp_date":null}}""")
        repository.validate(account)
        val request = server.takeRequest().requestUrl!!
        assertEquals("/service/player_api.php", request.encodedPath)
        assertEquals("a user", request.queryParameter("username")); assertEquals("p/a&ss", request.queryParameter("password"))
        assertFalse(account.toString().contains(account.password))
    }
    @Test fun rejectsInvalidAndExpiredAccounts() = runTest {
        respond("""{"user_info":{"auth":0}}""")
        try { repository.validate(account); fail() } catch (e: ProviderException) { assertTrue(e.message!!.contains("Login failed")) }
        respond("""{"user_info":{"auth":1,"status":"Expired"}}""")
        try { repository.validate(account); fail() } catch (e: ProviderException) { assertTrue(e.message!!.contains("Expired")) }
        respond("""{"user_info":{"auth":1,"status":"Active","exp_date":"1"}}""")
        try { repository.validate(account); fail() } catch (e: ProviderException) { assertTrue(e.message!!.contains("expired")) }
    }
    @Test fun parsesLiveMovieSeriesAndMissingArtwork() = runTest {
        respond("""[{"stream_id":12,"name":"News","category_id":"1","stream_icon":null},{"name":"Missing ID"}]""")
        val live = repository.catalog(account, ContentKind.LIVE).single()
        assertEquals("12", live.id); assertEquals("", live.image)
        respond("""[{"stream_id":"20","name":"Movie","container_extension":"mkv"}]""")
        assertEquals("mkv", repository.catalog(account, ContentKind.MOVIE).single().extension)
        respond("""[{"series_id":7,"name":"Series","cover":"https://example.com/cover.jpg"}]""")
        assertEquals("7", repository.catalog(account, ContentKind.SERIES).single().id)
    }
    @Test fun parsesCategoriesAndMovieDetails() = runTest {
        respond("""[{"category_id":1,"category_name":"News"},{"category_id":1,"category_name":"Duplicate"},{"category_name":"Missing ID"}]""")
        assertEquals(Category("1", "News"), repository.categories(account, ContentKind.LIVE).single())
        respond("""{"info":{"plot":"A story","genre":"Drama"},"movie_data":{"container_extension":"mkv"}}""")
        val details = repository.details(account, CatalogItem("1", "Film", ContentKind.MOVIE))
        assertEquals("A story", details.description); assertEquals("mkv", details.extension)
    }
    @Test fun sortsEpisodesNumericallyAndPreservesExtensions() = runTest {
        respond("""{"info":{"plot":"Story"},"episodes":{"10":[{"id":3,"episode_num":2,"title":"Third","container_extension":"mkv"}],"2":[{"id":2,"episode_num":10},{"id":1,"episode_num":1}]}}""")
        val details = repository.details(account, CatalogItem("9", "Show", ContentKind.SERIES))
        assertEquals(listOf("1", "2", "3"), details.episodes.map { it.item.id })
        assertEquals("mkv", details.episodes.last().item.extension)
    }
    @Test fun decodesGuideAndHandlesMissingGuide() = runTest {
        val title = Base64.getEncoder().encodeToString("Evening News".toByteArray())
        respond("""{"epg_listings":[{"title":"$title","start_timestamp":"100","stop_timestamp":"200"},{"title":"No times"}]}""")
        assertEquals(Programme("Evening News", 100, 200), repository.guide(account, "12").single())
        respond("{}"); assertTrue(repository.guide(account, "12").isEmpty())
    }
    @Test fun guideDescriptionsAreOptionalAndInvalidIntervalsAreDropped() = runTest {
        val description = Base64.getEncoder().encodeToString("Live coverage".toByteArray())
        respond("""{"epg_listings":[{"title":"News","description":"$description","start_timestamp":100,"stop_timestamp":200},{"title":"Zero","start_timestamp":100,"stop_timestamp":100},{"title":"Reversed","start_timestamp":200,"stop_timestamp":100},{"title":"Negative","start_timestamp":-1,"stop_timestamp":100}]}""")
        val guide = repository.guide(account, "12")
        assertEquals(1, guide.size)
        assertEquals("Live coverage", guide.single().description)
    }
    @Test fun emptyCatalogIsValidButMalformedAndDeniedResponsesFail() = runTest {
        respond("[]"); assertTrue(repository.catalog(account, ContentKind.LIVE).isEmpty())
        respond("not json"); try { repository.catalog(account, ContentKind.LIVE); fail() } catch (_: ProviderException) { }
        server.enqueue(MockResponse().setResponseCode(403))
        try { repository.catalog(account, ContentKind.LIVE); fail() } catch (e: ProviderException) { assertTrue(e.message!!.contains("Access denied")); assertFalse(e.message!!.contains(account.password)) }
        respond("{}"); try { repository.catalog(account, ContentKind.LIVE); fail() } catch (_: ProviderException) { }
    }
    @Test fun unreachableProviderReturnsActionableError() = runTest {
        val offline = MockWebServer().apply { start() }
        val disconnected = account.copy(baseUrl = offline.url("/").toString())
        offline.shutdown()
        try { repository.validate(disconnected); fail() } catch (e: ProviderException) {
            assertTrue(e.message!!.contains("Cannot reach the provider"))
            assertFalse(e.message!!.contains(disconnected.password))
        }
    }
    @Test fun parsesExplicitSeriesIdentityAndOptionalEpisodeMetadata() = runTest {
        respond("""{"info":{"plot":"Overview","mpaa":"TV-PG","language":"English"},"episodes":{"2":[{"id":44,"episode_num":5,"title":"Fifth","container_extension":"mkv","info":{"plot":"Episode overview","duration":"00:42:30","movie_image":"https://example.com/still.jpg"}}]}}""")
        val details=repository.details(account,CatalogItem("3","A Show",ContentKind.SERIES))
        val episode=details.episodes.single()
        assertEquals("3",episode.item.parentSeriesId); assertEquals("A Show",episode.item.parentSeriesName)
        assertEquals(2,episode.item.seasonNumber); assertEquals(5,episode.item.episodeNumber)
        assertEquals(2550L,episode.durationSeconds); assertEquals("Episode overview",episode.description)
        assertEquals("TV-PG",details.classification); assertEquals("English",details.language)
        respond("""{"user_info":{"auth":0}}""")
        try { repository.details(account,CatalogItem("3","A Show",ContentKind.SERIES)); fail() } catch (_: ProviderException) { }
    }
    @Test fun encodesPlaybackPathSegments() {
        val item = CatalogItem("42", "Movie", ContentKind.MOVIE, extension = "mkv")
        val url = repository.streamUrl(account, item)
        assertTrue(url.contains("/service/movie/a%20user/p%2Fa&ss/42.mkv"))
        assertTrue(repository.streamUrl(account, item.copy(kind = ContentKind.LIVE)).endsWith("/42.m3u8"))
        assertTrue(repository.streamUrl(account, item.copy(kind = ContentKind.SERIES)).contains("/series/"))
    }
}
