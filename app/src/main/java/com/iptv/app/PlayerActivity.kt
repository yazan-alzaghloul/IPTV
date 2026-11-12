package com.iptv.app

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import kotlinx.coroutines.*

interface PlaybackEngine {
    val player: Player
    fun open(url: String, position: Long, live: Boolean)
    fun release()
}
@UnstableApi
class Media3Engine(context: android.content.Context) : PlaybackEngine {
    private var released = false
    init { androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF) }
    override val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory()
            .setUserAgent("IPTV/1.1 Android").setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1)))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true).build()
    override fun open(url: String, position: Long, live: Boolean) {
        player.setMediaItem(MediaItem.fromUri(url))
        if (!live && position > 0) player.seekTo(position)
        player.prepare()
        player.playWhenReady = true
    }
    override fun release() { if (!released) { released = true; player.release() } }
}

@UnstableApi
class PlayerActivity : Activity() {
    private lateinit var item: CatalogItem
    private var credentials: Credentials? = null
    private lateinit var library: LibraryRepository
    private var engine: PlaybackEngine? = null
    private var session: MediaSession? = null
    private lateinit var video: PlayerView
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var qualityButton: Button
    private var screenLocked = false
    private var unlockButton: Button? = null
    private var queue: List<CatalogItem> = emptyList()
    private var previousLive: CatalogItem? = null
    private val activityScope = MainScope()
    private var itemChanged = false
    private var autoplayTask: Runnable? = null
    private val episodeButtons = mutableListOf<Button>()
    private var useTransportStream = false
    private var resumeWhenReady = true
    private var restartRequested = false
    private var aspect = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private val retries = RetryBudget()
    private var retryTask: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable {
        override fun run() { savePosition(); handler.postDelayed(this, 5_000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // URLs required by providers contain credentials. Never include them in library logs.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        library = LocalLibrary(PreferenceValues(this))
        val decoded = runCatching { Gson().fromJson(savedInstanceState?.getString("currentItem") ?: intent.getStringExtra("item"), CatalogItem::class.java) }.getOrNull()
        if (decoded == null) { finish(); return }
        item = decoded
        queue = runCatching { Gson().fromJson(intent.getStringExtra("queue") ?: "[]", Array<CatalogItem>::class.java).toList().take(50) }.getOrDefault(emptyList())
        credentials = runCatching { SecureCredentialStore(this).read() }.getOrNull()
        if (credentials == null) { finish(); return }
        resumeWhenReady = savedInstanceState?.getBoolean("playWhenReady", true) ?: true
        restartRequested = savedInstanceState == null && intent.getBooleanExtra("restart", false)
        useTransportStream = savedInstanceState?.getBoolean("transportStream") ?: false
        aspect = savedInstanceState?.getInt("aspect") ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = PlayerView(this).apply {
            keepScreenOn = true
            resizeMode = aspect
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowSubtitleButton(true)
            setShowNextButton(false); setShowPreviousButton(false)
            setShowFastForwardButton(item.kind != ContentKind.LIVE); setShowRewindButton(item.kind != ContentKind.LIVE)
            controllerShowTimeoutMs = 4_000
            findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                setPlayedColor(0xFF7C3AED.toInt()); setScrubberColor(0xFFF8FAFC.toInt()); setBufferedColor(0xFF64748B.toInt())
            }
        }
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF5080B12.toInt(), 0xD0080B12.toInt(), 0x00080B12))
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(button("Back") { finish() })
        title = TextView(this).apply {
            text = item.name; textSize = 22f; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL); setTextColor(Color.WHITE); maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(16), 0, 0, 0)
        }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(heading)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("Aspect") { selectAspect() })
        qualityButton = button("Quality") { selectQuality() }.apply { visibility = View.GONE }
        controls.addView(qualityButton)
        controls.addView(button("Info") { diagnostics() })
        if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
            controls.addView(button(getString(R.string.lock_controls)) { setScreenLocked(true) })
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) controls.addView(button("Picture-in-picture") {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
                .onFailure { Toast.makeText(this, localizedUiLabel(this, "Picture-in-picture is unavailable on this device."), Toast.LENGTH_SHORT).show() }
        })
        if (item.parentSeriesId != null) {
            episodeButtons += button("Previous episode") { switchEpisode(-1) }.also { controls.addView(it); it.isEnabled = false }
            episodeButtons += button("Next episode") { switchEpisode(1) }.also { controls.addView(it); it.isEnabled = false }
            episodeButtons += button("Episodes") { episodeBrowser() }.also { controls.addView(it); it.isEnabled = false }
            val autoplay = button(getString(if (PlaybackPreferences(this).autoplay) R.string.autoplay_on else R.string.autoplay_off)) {}
            autoplay.setOnClickListener {
                val preferences = PlaybackPreferences(this)
                preferences.autoplay = !preferences.autoplay
                autoplay.text = getString(if (preferences.autoplay) R.string.autoplay_on else R.string.autoplay_off)
                autoplay.contentDescription = autoplay.text
            }
            controls.addView(autoplay)
        }
        if (item.kind == ContentKind.LIVE && queue.size > 1) {
            controls.addView(button("Channel −") { switchChannel(-1) })
            controls.addView(button("Channel +") { switchChannel(1) })
            controls.addView(button("Previous channel") { previousLive?.let { switchTo(it) } })
        }
        header.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(controls) })
        frame.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(0xF0080B12.toInt()); visibility = View.GONE
        }
        errorText = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        errorPanel.addView(errorText)
        errorPanel.addView(button("Retry playback") { open(manual = true) })
        if (item.kind == ContentKind.LIVE) errorPanel.addView(button("Switch HLS / MPEG-TS") {
            useTransportStream = !useTransportStream; open(manual = true)
        })
        errorPanel.addView(button("Back to details") { finish() })
        frame.addView(errorPanel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        unlockButton = button(getString(R.string.unlock_controls)) { setScreenLocked(false) }.apply { visibility = View.GONE }
        frame.addView(unlockButton, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) })
        video.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visible -> header.visibility = if (isInPictureInPictureMode || screenLocked) View.GONE else visible })
        setContentView(frame)
        intent.getStringExtra("episodeQueue")?.let { token ->
            val c = credentials ?: return@let
            activityScope.launch {
                queue = withContext(Dispatchers.IO) { VodCatalogStore.get(this@PlayerActivity).loadQueue(accountScope(c), token) }
                updateEpisodeButtons()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (!::video.isInitialized || credentials == null) return
        val playback = Media3Engine(this)
        engine = playback
        video.player = playback.player
        session = MediaSession.Builder(this, playback.player).build()
        playback.player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val variants = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.sumOf { group ->
                    (0 until group.length).count { group.isTrackSupported(it) }
                }
                qualityButton.visibility = if (variants > 1) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                if (screenLocked) setScreenLocked(false)
                val transient = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                val delay = if (transient) retries.nextDelayMs() else null
                errorText.text = when {
                    delay != null -> getString(R.string.reconnecting, retries.attempts)
                    transient -> "Connection interrupted. Check your network, then retry."
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The provider refused this stream. Check your subscription or try again later."
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This device cannot decode this stream. Try another title or audio track."
                    error.errorCode in 6000..6999 -> "This stream requires a supported DRM license configuration."
                    else -> "This stream is unavailable or unsupported. Retry or choose another title."
                }
                errorText.text = localizedUiLabel(this@PlayerActivity, errorText.text.toString())
                errorPanel.visibility = View.VISIBLE
                if (delay != null) {
                    retryTask?.let { handler.removeCallbacks(it) }
                    retryTask = Runnable { open(manual = false) }.also { handler.postDelayed(it, delay) }
                } else errorPanel.getChildAt(1).requestFocus()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    savePosition()
                    if (item.parentSeriesId != null && PlaybackPreferences(this@PlayerActivity).autoplay) {
                        val endedKey = item.key
                        autoplayTask = Runnable { if (item.key == endedKey && engine?.player?.playbackState == Player.STATE_ENDED) switchEpisode(1) }
                            .also { handler.postDelayed(it, 1_000) }
                    }
                }
            }
        })
        open(manual = false)
        playback.player.playWhenReady = resumeWhenReady
        video.requestFocus(); video.showController()
        handler.postDelayed(checkpoint, 5_000)
    }
    private fun open(manual: Boolean) {
        val c = credentials ?: return
        val playback = engine ?: return
        retryTask?.let { handler.removeCallbacks(it) }; retryTask = null
        autoplayTask?.let { handler.removeCallbacks(it) }; autoplayTask = null
        if (manual) retries.reset()
        val position = if (restartRequested) 0L else if (itemChanged) library.position(c, item) else playback.player.currentPosition.takeIf { it > 0 } ?: library.position(c, item)
        restartRequested = false; itemChanged = false
        val base = XtreamRepository().streamUrl(c, item)
        val url = if (useTransportStream && item.kind == ContentKind.LIVE) base.removeSuffix("m3u8") + "ts" else base
        errorPanel.visibility = View.GONE
        playback.open(url, position, item.kind == ContentKind.LIVE)
        video.requestFocus()
    }
    private fun switchChannel(delta: Int) {
        val index = queue.indexOfFirst { it.key == item.key }
        if (index >= 0) queue.getOrNull(index + delta)?.let(::switchTo)
    }
    private fun switchTo(next: CatalogItem) {
        if (next.key == item.key) return
        savePosition(); if (item.kind == ContentKind.LIVE) previousLive = item
        item = next; itemChanged = true
        title.text = next.parentSeriesName?.let { "$it · S${next.seasonNumber} E${next.episodeNumber} · ${next.name}" } ?: next.name; useTransportStream = false; retries.reset()
        open(manual = false) // Reuse the same player and MediaSession; never open a second stream.
        video.showController(); updateEpisodeButtons()
    }
    private fun switchEpisode(delta: Int) {
        val index = queue.indexOfFirst { it.key == item.key }
        if (index >= 0) queue.getOrNull(index + delta)?.let(::switchTo)
    }
    private fun updateEpisodeButtons() {
        if (episodeButtons.size < 3) return
        val index = queue.indexOfFirst { it.key == item.key }
        episodeButtons[0].isEnabled = index > 0
        episodeButtons[1].isEnabled = index >= 0 && index < queue.lastIndex
        episodeButtons[2].isEnabled = queue.isNotEmpty()
    }
    private fun episodeBrowser() {
        val labels = queue.map { "S${it.seasonNumber} · E${it.episodeNumber}   ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(item.parentSeriesName ?: localizedUiLabel(this, "Episodes")).setItems(labels) { _, index -> switchTo(queue[index]) }
            .setNegativeButton(localizedUiLabel(this, "Cancel"), null).show()
    }
    private fun savePosition() {
        val c = credentials ?: return
        val player = engine?.player ?: return
        if (player.playbackState == Player.STATE_ENDED) library.savePosition(c, item, player.duration.coerceAtLeast(0), player.duration)
        else if (player.playbackState == Player.STATE_READY || player.currentPosition > 0)
            library.savePosition(c, item, player.currentPosition, player.duration)
    }
    private fun selectAspect() {
        val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        AlertDialog.Builder(this).setTitle(localizedUiLabel(this, "Aspect ratio")).setSingleChoiceItems(arrayOf("Fit", "Fill", "Zoom").map { localizedUiLabel(this, it) }.toTypedArray(), modes.indexOf(aspect)) { dialog, which ->
            aspect = modes[which]; video.resizeMode = aspect; dialog.dismiss()
        }.setNegativeButton(localizedUiLabel(this, "Cancel"), null).show()
    }
    private fun selectQuality() {
        val player = engine?.player ?: return
        val choices = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val format = group.getTrackFormat(index)
                Triple(group, index, if (format.height > 0) "${format.height}p${if (format.bitrate > 0) " · ${format.bitrate / 1000} kbps" else ""}" else getString(R.string.video_track_number, index + 1))
            }
        }
        AlertDialog.Builder(this).setTitle(localizedUiLabel(this, "Playback quality")).setItems((listOf(localizedUiLabel(this, "Automatic")) + choices.map { it.third }).toTypedArray()) { _, which ->
            val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            if (which > 0) choices[which - 1].let { builder.addOverride(TrackSelectionOverride(it.first.mediaTrackGroup, it.second)) }
            player.trackSelectionParameters = builder.build()
        }.setNegativeButton(localizedUiLabel(this, "Cancel"), null).show()
    }
    private fun diagnostics() {
        val player = engine?.player ?: return
        val format = (player as? ExoPlayer)?.videoFormat
        val description = getString(R.string.player_diagnostics, format?.sampleMimeType ?: getString(R.string.video_info_missing), format?.width ?: 0, format?.height ?: 0, player.totalBufferedDuration / 1000, retries.attempts, if (useTransportStream) "MPEG-TS" else "Automatic / HLS")
        AlertDialog.Builder(this).setTitle(localizedUiLabel(this, "Playback diagnostics")).setMessage(description).setPositiveButton(localizedUiLabel(this, "Close"), null).show()
    }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        if (::video.isInitialized) { video.useController = !inPip && !screenLocked; header.visibility = if (inPip || screenLocked) View.GONE else View.VISIBLE; unlockButton?.visibility = if (!inPip && screenLocked) View.VISIBLE else View.GONE }
    }
    override fun onPause() {
        savePosition() // Commit before the underlying details activity resumes and refreshes its library.
        super.onPause()
    }
    override fun onStop() {
        autoplayTask?.let { handler.removeCallbacks(it) }; autoplayTask = null
        handler.removeCallbacks(checkpoint); retryTask?.let { handler.removeCallbacks(it) }; retryTask = null
        resumeWhenReady = engine?.player?.playWhenReady ?: resumeWhenReady
        savePosition(); session?.release(); session = null
        if (::video.isInitialized) video.player = null
        engine?.release(); engine = null
        super.onStop()
    }
    private fun setScreenLocked(locked: Boolean) {
        screenLocked = locked
        video.useController = !locked && !isInPictureInPictureMode
        unlockButton?.visibility = if (locked && !isInPictureInPictureMode) View.VISIBLE else View.GONE
        if (locked) { video.hideController(); header.visibility = View.GONE }
        else video.showController()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenLocked && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) setScreenLocked(false)
            return true
        }
        if (::video.isInitialized && item.kind == ContentKind.LIVE && event.action == KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            switchChannel(if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1); return true
        }
        if (::video.isInitialized && event.keyCode != KeyEvent.KEYCODE_BACK && errorPanel.visibility != View.VISIBLE && video.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("transportStream", useTransportStream)
        outState.putBoolean("playWhenReady", engine?.player?.playWhenReady ?: resumeWhenReady)
        outState.putInt("aspect", aspect); outState.putString("currentItem", Gson().toJson(item))
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { activityScope.cancel(); super.onDestroy() }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = localizedUiLabel(this@PlayerActivity, label); contentDescription = text; isAllCaps = false; textSize = 14f
        minimumHeight = dp(48); minimumWidth = dp(64)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setTextColor(Color.WHITE)
        val states = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(0xFF7C3AED.toInt(), 0xFF6D28D9.toInt(), 0xFF151B28.toInt(), 0xFF242D3E.toInt()))
        backgroundTintList = states
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF242D3E.toInt()) }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
