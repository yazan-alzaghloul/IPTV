package com.iptv.app

import androidx.activity.compose.setContent
import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
@UnstableApi
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: MockWebServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val failStream = AtomicBoolean(false)
    private val streamRequests = java.util.concurrent.atomic.AtomicInteger(0)
    private val failCatalog = AtomicBoolean(false)
    private val video by lazy { instrumentation.context.assets.open("sample.mp4").use { it.readBytes() } }
    @Before fun setup() {
        SecureCredentialStore(context).clear(); LocalLibrary(PreferenceValues(context)).clear()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("vod_playback", Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath == "/poster.jpg") return MockResponse().setHeader("Content-Type", "image/jpeg")
                    .setBody(Buffer().write(instrumentation.context.assets.open("poster.jpg").use { it.readBytes() }))
                if (!url.encodedPath.endsWith("player_api.php")) {
                    streamRequests.incrementAndGet()
                    if (failStream.get()) return MockResponse().setResponseCode(503)
                    if (url.encodedPath.endsWith(".m3u8")) return MockResponse().setHeader("Content-Type", "application/x-mpegURL").setBody("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:10.0,\n/segment.ts\n")
                    val data = if (url.encodedPath.endsWith(".ts")) instrumentation.context.assets.open("sample.ts").use { it.readBytes() } else video
                    val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                    if (start >= data.size) return MockResponse().setResponseCode(416)
                    return MockResponse().setResponseCode(if (start > 0) 206 else 200)
                        .setHeader("Accept-Ranges", "bytes").setHeader("Content-Type", if (url.encodedPath.endsWith(".ts")) "video/mp2t" else "video/mp4")
                        .apply { if (start > 0) setHeader("Content-Range", "bytes $start-${data.size - 1}/${data.size}") }
                        .setBody(Buffer().write(data, start, data.size - start))
                }
                if (failCatalog.get()) return MockResponse().setResponseCode(503)
                val action = url.queryParameter("action")
                val now = System.currentTimeMillis() / 1000
                fun encoded(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
                val body = when (action) {
                    null -> """{"user_info":{"auth":1,"status":"Active"}}"""
                    "get_live_categories", "get_vod_categories", "get_series_categories" -> """[{"category_id":"1","category_name":"Test category"}]"""
                    "get_live_streams" -> """[{"stream_id":1,"name":"Fixture News","category_id":"1"},{"stream_id":5,"name":"Fixture Sports","category_id":"1"}]"""
                    "get_vod_streams" -> """[{"stream_id":2,"name":"Fixture Movie","category_id":"1","container_extension":"mp4","stream_icon":"${server.url("/poster.jpg")}","backdrop_path":["${server.url("/poster.jpg")}"],"year":"2008","rating":"8.0","genre":"Drama","language":"English","releaseDate":"2008-04-10"}]"""
                    "get_series" -> """[{"series_id":3,"name":"Fixture Series","category_id":"1"}]"""
                    "get_vod_info" -> """{"info":{"plot":"Fixture movie description","genre":"Drama","year":"2008","duration":"00:10:00","rating":"8.0","cast":"Fixture Cast","director":"Fixture Director","backdrop_path":["${server.url("/poster.jpg")}"]},"movie_data":{"container_extension":"mp4"}}"""
                    "get_series_info" -> """{"info":{"plot":"Fixture series description","genre":"Drama","backdrop_path":["${server.url("/poster.jpg")}"]},"episodes":{"1":[{"id":4,"episode_num":1,"title":"Pilot","container_extension":"mp4","info":{"plot":"Episode one overview","duration_secs":2,"movie_image":"${server.url("/poster.jpg")}"}},{"id":6,"episode_num":2,"title":"Second episode","container_extension":"mp4","info":{"plot":"Episode two overview","duration":"00:02"}}],"2":[{"id":7,"episode_num":1,"title":"New season","container_extension":"mp4"}]}}"""
                    "get_short_epg" -> """{"epg_listings":[{"title":"${encoded("News now")}","start_timestamp":"${now - 600}","stop_timestamp":"${now + 600}"},{"title":"${encoded("News next")}","start_timestamp":"${now + 600}","stop_timestamp":"${now + 1200}"}]}"""
                    else -> "[]"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync {
            (ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList() +
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STARTED).toList())
                .filterIsInstance<PlayerActivity>().distinct().forEach { it.finish() }
        }
        scenario.close(); server.shutdown()
        SecureCredentialStore(context).clear(); LocalLibrary(PreferenceValues(context)).clear()
    }
    private fun login() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Host or provider URL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Host or provider URL").performScrollTo().performTextInput(server.url("/service/").toString())
        compose.onNodeWithText("Username", substring = false).performScrollTo().performTextInput("fixture")
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextInput("fixture-password")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        waitFor("Your next great watch")
        navigate(Section.LIVE)
        waitFor("Fixture News")
    }
    private fun navigate(section: Section, browse: Boolean = true) {
        if (compose.onAllNodesWithTag("nav_${section.name}").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("nav_LIBRARY").performClick()
        val target = compose.onNodeWithTag("nav_${section.name}")
        if (runCatching { target.assertIsDisplayed() }.isFailure) target.performScrollTo()
        target.performClick()
        if (browse && section in listOf(Section.MOVIES,Section.SERIES)) {
            val label="Browse ${section.label.lowercase()}"
            waitFor(label); compose.onNodeWithText(label).performClick()
        }
    }
    private fun waitFor(text: String) {
        try {
            compose.waitUntil(15_000) {
                if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) true
                else if (compose.onAllNodesWithTag("vod_details").fetchSemanticsNodes().isNotEmpty())
                    runCatching { compose.onNodeWithTag("vod_details").performScrollToNode(hasText(text)); true }.getOrDefault(false)
                else false
            }
        } catch (e: ComposeTimeoutException) {
            capture("failure")
            java.io.File(context.filesDir, "screenshots/failure-tree.txt").writeText(compose.onRoot().printToString())
            throw e
        }
    }
    private fun openNews() {
        if (compose.onAllNodesWithTag("channel_LIVE:1").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("channel_LIVE:1").performClick()
        else compose.onNodeWithText("Fixture News").performClick()
    }
    private fun back() { Espresso.pressBack(); compose.waitForIdle() }
    @Test fun loginValidationAndCatalogFavoritesEpisodesAndCleanup() {
        compose.onNodeWithText("Connect", substring = false).performScrollTo().performClick()
        waitFor("Enter your username and password.")
        login()
        capture("catalog")
        openNews()
        waitFor("News now"); compose.onNodeWithText("News next").assertExists()
        capture("channel-details")
        compose.onNodeWithText("Add to favorites").performClick()
        back()
        navigate(Section.FAVORITES)
        waitFor("Fixture News")
        openNews()
        compose.onNodeWithText("Remove from favorites").performClick()
        back(); waitFor("Keep your favorites here")
        navigate(Section.MOVIES); waitFor("Fixture Movie")
        compose.onNodeWithText("Search movies").performTextInput("nonexistent")
        compose.onNodeWithText("Search movies").performImeAction()
        waitFor("No matching titles")
        compose.onNodeWithText("Clear", substring = false).performClick(); waitFor("Fixture Movie")
        compose.onNodeWithText("Fixture Movie").performClick(); waitFor("Fixture movie description")
        back()
        navigate(Section.SERIES); waitFor("Fixture Series")
        compose.onNodeWithText("Fixture Series").performClick(); waitFor("S1 · E1 Pilot")
        back()
        compose.onNodeWithText("Search series").performTextInput("private search")
        compose.onNodeWithText("Search series").performImeAction()
        val previousAccount = SecureCredentialStore(context).read()!!
        compose.onNodeWithTag("header_more").performClick()
        compose.onNodeWithTag("sign_out").performClick()
        compose.onAllNodesWithText("Sign out", substring = false).onLast().performClick()
        waitFor("Connect")
        assertNull(SecureCredentialStore(context).read())
        assertTrue(context.getSharedPreferences("local_library", Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(0, VodCatalogStore.get(context).page(accountScope(previousAccount), ContentKind.MOVIE).total)
        assertEquals(0, VodCatalogStore.get(context).page(accountScope(previousAccount), ContentKind.SERIES).total)
        login(); navigate(Section.SERIES, browse = false)
        waitFor("Browse series")
        compose.onNodeWithText("Browse series").performClick()
        compose.onNodeWithText("Search series").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun savedCredentialsAreEncryptedAndRestored() {
        login()
        val credentials = SecureCredentialStore(context).read()!!
        assertEquals("fixture", credentials.username)
        val saved = context.getSharedPreferences("account_vault", Context.MODE_PRIVATE).all.toString()
        assertFalse(saved.contains("fixture-password")); assertFalse(saved.contains("fixture"))
        scenario.recreate(); waitFor("Fixture News")
    }
    @Test fun catalogFailureCanBeRetriedAndRemoteFocusWorks() {
        login()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("channel_LIVE:1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("channel_LIVE:1").assertIsFocused()
        capture("remote-focus")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("Watch live")
        back()
        failCatalog.set(true)
        navigate(Section.MOVIES)
        compose.onNodeWithTag("vod_refresh").performClick()
        waitFor("Provider returned HTTP 503. Try again later.")
        failCatalog.set(false)
        compose.onNodeWithText("Try again").performClick(); waitFor("Fixture Movie")
    }
    @Test fun onDemandPlaybackPauseSeekResumeAndRetry() {
        login()
        navigate(Section.MOVIES); waitFor("Fixture Movie")
        compose.onNodeWithText("Fixture Movie").performClick(); waitFor("Fixture movie description")
        failStream.set(true)
        compose.onNodeWithTag("vod_play").performScrollTo().performClick()
        waitNative(15_000) { activity -> activity.window.decorView.findText("Retry playback") != null }
        failStream.set(false)
        instrumentation.runOnMainSync { resumedPlayer().window.decorView.findText("Retry playback")!!.performClick() }
        waitNative(15_000) { activity -> player(activity)?.let { it.playbackState == Player.STATE_READY && it.currentPosition > 0 } == true }
        instrumentation.runOnMainSync {
            val activity = resumedPlayer()
            val p = player(activity)!!
            findPlayer(activity.window.decorView)!!.showController()
            activity.window.decorView.findText("Lock controls")?.let { lock ->
                lock.performClick()
                assertFalse(findPlayer(activity.window.decorView)!!.useController)
                activity.window.decorView.findText("Unlock controls")!!.performClick()
                assertTrue(findPlayer(activity.window.decorView)!!.useController)
                assertSame(p, player(activity))
            }
            p.pause(); assertFalse(p.playWhenReady)
            p.seekTo(500); p.play()
        }
        waitNative(10_000) { player(it)?.currentPosition?.let { pos -> pos >= 500 } == true }
        instrumentation.runOnMainSync { resumedPlayer().finish() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("vod_play").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun liveHlsPlaybackAndBackNavigation() {
        login(); openNews(); waitFor("News now")
        compose.onNodeWithText("Watch live").performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitFor("Watch live")
    }
    @Test fun homeAndAppearanceSettingsUseRealCatalogAndPersist() {
        login(); navigate(Section.HOME); waitFor("Your next great watch")
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Rating 8.0", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("FEATURED IN YOUR LIBRARY").assertExists()
        capture("modern-home")
        navigate(Section.SETTINGS); waitFor("Appearance")
        compose.onNodeWithText("Light", substring = false).performClick()
        assertEquals("light", AppPreferences(context).read().theme)
        compose.onNodeWithText("Cinematic dark").performClick()
        capture("modern-settings")
        scenario.recreate(); waitFor("Appearance")
        assertEquals("dark", AppPreferences(context).read().theme)
        navigate(Section.HOME); waitFor("Your next great watch")
    }
    @Test fun universalSearchFindsContentAcrossKinds() {
        login(); navigate(Section.SEARCH); waitFor("Search your library")
        compose.onNodeWithText("Search your library").performTextInput("Fixture Movie")
        compose.onNodeWithText("Search your library").performImeAction()
        waitFor("Fixture Movie")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("card_MOVIE:2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card_MOVIE:2").performClick()
        waitFor("Fixture movie description")
    }
    @Test fun channelSwitchKeepsTheSamePlayerAndAspectControlsWork() {
        login(); openNews(); waitFor("News now")
        compose.onNodeWithText("Watch live").performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY }
        var original: Player? = null
        instrumentation.runOnMainSync { original = player(resumedPlayer()) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_CHANNEL_UP)
        waitNative(20_000) { activity ->
            activity.window.decorView.findText("Fixture Sports") != null && player(activity)?.playbackState == Player.STATE_READY
        }
        instrumentation.runOnMainSync {
            val activity = resumedPlayer()
            assertSame(original, player(activity))
            findPlayer(activity.window.decorView)!!.showController()
            activity.window.decorView.findText("Aspect")!!.performClick()
        }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Zoom"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        instrumentation.runOnMainSync {
            assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, findPlayer(resumedPlayer().window.decorView)!!.resizeMode)
            resumedPlayer().finish()
        }
        waitFor("Watch live")
    }
    @Test fun pictureInPictureKeepsTheVideoActivityAliveAndReturnsToDetails() {
        org.junit.Assume.assumeTrue(context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        login(); navigate(Section.MOVIES); waitFor("Fixture Movie")
        compose.onNodeWithText("Fixture Movie").performClick(); waitFor("Fixture movie description")
        compose.onNodeWithTag("vod_play").performScrollTo().performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY }
        lateinit var activity: PlayerActivity
        instrumentation.runOnMainSync {
            activity = resumedPlayer()
            findPlayer(activity.window.decorView)!!.showController()
            activity.window.decorView.findText("Picture-in-picture")!!.performClick()
        }
        compose.waitUntil(10_000) {
            var inPip = false
            instrumentation.runOnMainSync { inPip = activity.isInPictureInPictureMode }
            inPip
        }
        instrumentation.runOnMainSync {
            assertNotNull(findPlayer(activity.window.decorView)!!.player)
            activity.finish()
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("vod_play").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun moviesLandingDetailsAndPagedBrowseUseProviderData() {
        login(); navigate(Section.MOVIES, browse = false)
        waitFor("Fixture movie description")
        capture("vod-movies-home")
        compose.onNodeWithTag("vod_landing").performScrollToNode(hasText("Featured movies"))
        compose.onNodeWithText("Featured movies").assertIsDisplayed()
        compose.onNodeWithTag("vod_landing").performScrollToIndex(0)
        compose.onNodeWithText("More details").performScrollTo().performClick()
        waitFor("Fixture movie description")
        compose.onNodeWithTag("vod_watchlist").performScrollTo().performClick()
        waitFor("Remove from watchlist")
        capture("vod-movie-details")
        back()
        compose.onNodeWithText("Browse movies").performClick()
        compose.onNodeWithTag("vod_view_toggle").performClick()
        compose.onNodeWithTag("card_MOVIE:2").assertExists()
        compose.onNodeWithText("Search movies").performTextInput("Fixture Movie")
        compose.onNodeWithText("Search movies").performImeAction()
        compose.waitUntil(10_000) { LocalLibrary(PreferenceValues(context)).searches(SecureCredentialStore(context).read()!!).contains("Fixture Movie") }
        capture("vod-movie-browse")
    }
    @Test fun seriesProgressWatchedFlagsAndSeasonSelectionPersist() {
        login(); navigate(Section.SERIES)
        waitFor("Fixture Series"); compose.onNodeWithTag("card_SERIES:3").performClick()
        waitFor("S1 · E1 Pilot")
        compose.onNodeWithTag("vod_details").performScrollToNode(hasTestTag("episode_play_4"))
        compose.onNodeWithTag("episode_play_4").performScrollTo().performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY }
        instrumentation.runOnMainSync { player(resumedPlayer())!!.apply { pause(); seekTo(200) }; resumedPlayer().finish() }
        waitFor("Continue watching — Season 1, Episode 1")
        compose.onNodeWithTag("vod_details").performScrollToNode(hasTestTag("episode_mark_4"))
        compose.onNodeWithTag("episode_mark_4").performScrollTo().performClick()
        waitFor("Next — Season 1, Episode 2")
        waitFor("Season 2")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("Season 2", substring = false).performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Season 2", substring = false).assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("S2 · E1 New season")
        capture("vod-series-details")
        val account=SecureCredentialStore(context).read()!!
        assertEquals(2,LocalLibrary(PreferenceValues(context)).season(account,"3"))
        assertTrue("SERIES:4" in LocalLibrary(PreferenceValues(context)).watched(account))
        back(); compose.onNodeWithTag("card_SERIES:3").performClick()
        waitFor("S2 · E1 New season")
    }
    @Test fun nextPreviousAndAutoplayEpisodesReuseTheAuthenticatedPlayer() {
        login(); navigate(Section.SERIES); waitFor("Fixture Series")
        compose.onNodeWithTag("card_SERIES:3").performClick(); waitFor("S1 · E1 Pilot")
        compose.onNodeWithTag("vod_details").performScrollToNode(hasTestTag("episode_play_4"))
        compose.onNodeWithTag("episode_play_4").performScrollTo().performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY && it.window.decorView.findText("Next episode")?.isEnabled == true }
        var original: Player? = null
        instrumentation.runOnMainSync {
            val a=resumedPlayer(); original=player(a); original!!.pause(); findPlayer(a.window.decorView)!!.showController()
            a.window.decorView.findText("Next episode")!!.performClick()
        }
        waitNative(20_000) { player(it)?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment == "6.mp4" && player(it)?.playbackState == Player.STATE_READY }
        instrumentation.runOnMainSync {
            val a=resumedPlayer(); assertSame(original,player(a)); findPlayer(a.window.decorView)!!.showController()
            a.window.decorView.findText("Previous episode")!!.performClick()
        }
        waitNative(20_000) { player(it)?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment == "4.mp4" && player(it)?.playbackState == Player.STATE_READY }
        instrumentation.runOnMainSync {
            val a=resumedPlayer(); PlaybackPreferences(context).autoplay=true
            player(a)!!.apply { seekTo((duration-100).coerceAtLeast(0)); play() }
        }
        waitNative(20_000) { player(it)?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment == "6.mp4" }
        instrumentation.runOnMainSync { assertSame(original,player(resumedPlayer())); resumedPlayer().finish() }
    }
    @Test fun liveWorkspaceGuidePreviewAndFullscreenUseOneConnectionOwner() {
        login()
        compose.onNodeWithTag("nav_MOVIES").assertExists()
        compose.onNodeWithTag("nav_SERIES").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live_preview_toggle").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Browsing must not open video streams", 0, streamRequests.get())
        if (compose.onAllNodesWithTag("live_guide_tab").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("live_guide_tab").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("epg_timeline").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide_now").performScrollTo().performClick()
        capture("cinema-live-guide")
        compose.onNodeWithTag("live_preview_toggle").performClick()
        compose.waitUntil(20_000) {
            var ready = false
            scenario.onActivity { ready = findPlayer(it.window.decorView)?.player?.playbackState == Player.STATE_READY }
            ready
        }
        capture("cinema-live-preview")
        compose.onNodeWithTag("live_fullscreen").performClick()
        waitNative(20_000) { player(it)?.playbackState == Player.STATE_READY }
        scenario.onActivity { assertNull("Preview must release before fullscreen", findPlayer(it.window.decorView)?.player) }
        instrumentation.runOnMainSync { resumedPlayer().finish() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live_workspace").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start preview").assertExists()
    }
    @Test fun posterFocusReturnsToTheSameRailAfterDetails() {
        login(); navigate(Section.MOVIES, browse = false)
        waitFor("Fixture movie description")
        compose.onNodeWithTag("vod_landing").performScrollToNode(hasText("Featured movies"))
        val poster = hasTestTag("card_MOVIE:2") and hasAnyAncestor(hasTestTag("rail_Featured movies"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(poster).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(poster).assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("Fixture movie description")
        back()
        compose.waitUntil(10_000) { compose.onAllNodes(poster and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        capture("cinema-restored-poster-focus")
    }
    @Test fun tvSidebarExpandsWithoutLosingRemoteFocus() {
        org.junit.Assume.assumeTrue(context.resources.configuration.screenWidthDp >= 840)
        login()
        scenario.onActivity { activity ->
            val vm = androidx.lifecycle.ViewModelProvider(activity)[AppViewModel::class.java]
            activity.setContent {
                IptvTheme(vm.state.appearance) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalCinemaTv provides true) {
                        androidx.compose.material3.Surface { PremiumShell(vm) {} }
                    }
                }
            }
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("nav_HOME").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("nav_HOME").assertIsFocused()
        compose.onNodeWithTag("nav_MOVIES").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("nav_MOVIES").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("Browse movies")
        compose.onNodeWithTag("nav_MOVIES").assertIsFocused()
        capture("cinema-tv-sidebar")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = java.io.File(context.filesDir, "screenshots").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun resumedPlayer() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<PlayerActivity>().first()
    private fun player(activity: PlayerActivity): Player? = findPlayer(activity.window.decorView)?.player
    private fun findPlayer(view: android.view.View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun android.view.View.findText(text: String): android.widget.TextView? {
        if (this is android.widget.TextView && this.text.toString() == text && visibility == android.view.View.VISIBLE) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findText(text)?.let { return it }
        return null
    }
    private fun waitNative(timeout: Long, check: (PlayerActivity) -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            var passed = false
            instrumentation.runOnMainSync { runCatching { passed = check(resumedPlayer()) } }
            if (passed) return
            Thread.sleep(100)
        }
        val directory=java.io.File(context.filesDir,"screenshots").apply { mkdirs() }
        java.io.File(directory,"native-failure.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        instrumentation.runOnMainSync {
            val activities=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            println("Resumed activities: "+activities.map { it.javaClass.simpleName })
            activities.filterIsInstance<PlayerActivity>().forEach { activity ->
                val p=player(activity)
                println("Player state=${p?.playbackState}, errorCode=${p?.playerError?.errorCode}, path=${p?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment}")
            }
        }
        fail("Native player condition did not become true within $timeout ms")
    }
}
