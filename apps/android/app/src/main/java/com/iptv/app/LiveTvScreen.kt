package com.iptv.app

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun LiveTvScreen(vm: AppViewModel, details: (CatalogItem) -> Unit, play: (CatalogItem) -> Unit) {
    val s = vm.state
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var guideTab by rememberSaveable { mutableStateOf(false) }
    var previewEnabled by rememberSaveable { mutableStateOf(false) }
    val favorites = remember(s.favorites) { s.favorites.map { it.key }.toSet() }
    val channels by produceState<List<CatalogItem>>(emptyList(), s.catalog, s.search, s.category, s.sort, onlyFavorites, favorites) {
        if (s.search.isNotBlank()) delay(180)
        value = withContext(Dispatchers.Default) {
            CatalogQuery.apply(s.catalog.filter { it.kind == ContentKind.LIVE && (!onlyFavorites || it.key in favorites) }, s.search, s.category, s.sort)
        }
    }
    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && channels.none { it.key == vm.state.livePreview?.key }) vm.previewChannel(channels.first())
    }
    val selected = s.livePreview?.takeIf { item -> channels.any { it.key == item.key } }
    // Selection leaving the filtered catalog must never leave a hidden stream playing.
    LaunchedEffect(selected?.key) { if (selected == null) previewEnabled = false }
    LaunchedEffect(selected?.key) {
        selected?.let { while (true) { delay(120_000); vm.previewChannel(it, true) } }
    }
    val launch: (CatalogItem) -> Unit = { previewEnabled = false; play(it) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis() / 1000; delay(15_000) } }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("live_workspace")) {
        val wide = maxWidth >= 760.dp
        val roomForPreview = maxHeight >= 580.dp && LocalDensity.current.fontScale < 1.4f
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (wide) LazyColumn(Modifier.width(164.dp).fillMaxHeight().padding(start = 20.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text(stringResource(R.string.ui_channel_groups), Modifier.padding(vertical = 12.dp), color = Muted, style = MaterialTheme.typography.labelSmall) }
                item { CategoryFilter(stringResource(R.string.ui_all_channels), s.category.isEmpty() && !onlyFavorites) { onlyFavorites = false; vm.category("") } }
                item { CategoryFilter(stringResource(R.string.ui_favorites), onlyFavorites) { onlyFavorites = !onlyFavorites } }
                items(s.categories, key = { it.id }) { c -> CategoryFilter(c.name, s.category == c.id && !onlyFavorites) { onlyFavorites = false; vm.category(c.id) } }
            }
            Column(Modifier.weight(1f).padding(horizontal = if (wide) 0.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.search, { vm.search(it.take(240)) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.ui_search_channels)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (s.search.isNotBlank()) IconButton({ vm.search("") }) { Icon(Icons.Default.Close, stringResource(R.string.ui_clear_search)) } }, shape = CinemaTokens.PosterShape, colors = cinemaFieldColors())
                if (!wide) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(s.category.isEmpty() && !onlyFavorites, { vm.category(""); onlyFavorites = false }, { Text(stringResource(R.string.ui_all_channels)) }, modifier = Modifier.focusRing()) }
                    item { FilterChip(onlyFavorites, { onlyFavorites = !onlyFavorites }, { Text(stringResource(R.string.ui_favorites)) }, modifier = Modifier.testTag("live_favorites").focusRing()) }
                    items(s.categories, key = { it.id }) { c -> FilterChip(s.category == c.id && !onlyFavorites, { vm.category(c.id); onlyFavorites = false }, { Text(c.name) }, modifier = Modifier.focusRing()) }
                }
                if (!wide && selected != null) {
                    LivePreview(vm, selected, previewEnabled, { previewEnabled = !previewEnabled }, launch, compact = true, showArtwork = previewEnabled || roomForPreview)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!guideTab, { guideTab = false }, { Text(stringResource(R.string.ui_channels)) }, modifier = Modifier.focusRing())
                        FilterChip(guideTab, { guideTab = true }, { Text(stringResource(R.string.ui_program_guide)) }, modifier = Modifier.testTag("live_guide_tab").focusRing())
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.channel_count, channels.size, channels.size), Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium)
                    var sorting by remember { mutableStateOf(false) }
                    Box {
                        CinemaTextButton({ sorting = true }, Modifier.focusRing()) { Text(s.sort.label) }
                        DropdownMenu(sorting, { sorting = false }) {
                            CatalogSort.entries.filter { it != CatalogSort.RATING }.forEach { order ->
                                DropdownMenuItem({ Text(order.label) }, { sorting = false; vm.sort(order) })
                            }
                        }
                    }
                    IconButton(vm::refresh, Modifier.focusRing()) { Icon(Icons.Default.Refresh, stringResource(R.string.ui_refresh_channels)) }
                }
                s.error?.let { ErrorState(it, vm::refresh) }
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading channels" })
                if (channels.isEmpty() && !s.busy) ErrorState(uiLabel(if (onlyFavorites) "No favorite channels yet. Save channels with the heart button." else "No channels match. Try another category or search."), { onlyFavorites = false; vm.category(""); vm.search(""); vm.refresh() })
                if (!wide && guideTab && selected != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuidePanel(s, now) { vm.previewChannel(selected, true) }
                } else {
                    val list = rememberLazyListState()
                    var lastFocus by rememberSaveable { mutableStateOf<String?>(null) }
                    val requester = remember { FocusRequester() }
                    val keyboard = LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
                    var restoreFocus by remember { mutableStateOf(true) }
                    LaunchedEffect(channels) {
                        if (channels.isNotEmpty() && restoreFocus) {
                            restoreFocus = false
                        if (keyboard && lastFocus != null) {
                            val index = channels.indexOfFirst { it.key == lastFocus }
                            if (index >= 0) { list.scrollToItem(index); withFrameNanos { }; runCatching { requester.requestFocus() } }
                        }
                        }
                    }
                    val numbers = remember(s.catalog) { s.catalog.withIndex().associate { it.value.key to it.index + 1 } }
                    if (channels.isNotEmpty()) LazyColumn(Modifier.weight(1f).focusGroup(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(channels, key = { it.key }) { channel ->
                            ChannelListItem(channel, numbers[channel.key] ?: 0, channel.key in favorites, selected?.key == channel.key,
                                if (selected?.key == channel.key) GuideLayout.current(s.liveGuide, now) else null, now,
                                Modifier.then(if (lastFocus == channel.key) Modifier.focusRequester(requester) else Modifier).onFocusChanged {
                                    if (it.hasFocus) { lastFocus = channel.key; vm.previewChannel(channel) }
                                }, { details(channel) }, { vm.toggle(channel) })
                        }
                    }
                }
            }
            if (wide) Column(Modifier.weight(1.05f).fillMaxHeight().padding(end = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected != null) {
                    LivePreview(vm, selected, previewEnabled, { previewEnabled = !previewEnabled }, launch)
                    Text(selected.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    GuidePanel(s, now) { vm.previewChannel(selected, true) }
                } else ErrorState(stringResource(R.string.ui_choose_a_channel_to_see_its_schedule), vm::refresh)
            }
        }
    }
}

@Composable fun CategoryFilter(label: String, selected: Boolean, click: () -> Unit) {
    Surface(onClick = click, modifier = Modifier.fillMaxWidth().focusRing(), color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = CinemaTokens.PosterShape) {
        Text(label, Modifier.padding(14.dp), color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Muted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable fun ChannelListItem(item: CatalogItem, number: Int, favorite: Boolean, selected: Boolean, programme: Programme?, now: Long,
    modifier: Modifier = Modifier, click: () -> Unit, toggle: () -> Unit) {
    Surface(onClick = click, modifier = modifier.fillMaxWidth().testTag("channel_${item.key}").focusRing(),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Panel, shape = CinemaTokens.PosterShape) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(number.toString().padStart(2, '0'), color = Muted, style = MaterialTheme.typography.labelMedium)
            Artwork(item, Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(programme?.title ?: stringResource(R.string.ui_live_channel), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (programme != null) PlaybackProgress(GuideLayout.progress(programme, now), Modifier.fillMaxWidth())
            }
            IconButton(toggle, Modifier.size(48.dp).focusRing()) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(if (favorite) R.string.unfavorite_channel else R.string.favorite_channel, item.name), tint = if (favorite) AccentInk else Muted)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun LivePreview(vm: AppViewModel, item: CatalogItem, enabled: Boolean, toggle: () -> Unit,
    play: (CatalogItem) -> Unit, compact: Boolean = false, showArtwork: Boolean = true) {
    var engine by remember { mutableStateOf<Media3Engine?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember(item.key) { mutableStateOf(false) }
    val context = LocalContext.current
    if (enabled) {
        LifecycleStartEffect(Unit) {
            val active = Media3Engine(context)
            active.player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { loading = state == Player.STATE_BUFFERING }
                override fun onPlayerError(failure: PlaybackException) { error = true; loading = false }
            })
            engine = active
            onStopOrDispose { active.release(); engine = null }
        }
        LaunchedEffect(engine, item.key) { engine?.let { active -> vm.previewUrl(item)?.let { error = false; loading = true; active.open(it, 0, true) } } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showArtwork) Box(Modifier.fillMaxWidth().then(if (compact) Modifier.height(156.dp) else Modifier.aspectRatio(16f / 9)).clip(CinemaTokens.PosterShape).background(Color.Black), contentAlignment = Alignment.Center) {
            if (enabled && engine != null) AndroidView(factory = { PlayerView(it).apply { useController = false } },
                update = { it.player = engine?.player }, modifier = Modifier.fillMaxSize())
            else {
                Artwork(item, Modifier.fillMaxSize().padding(32.dp))
                Text(stringResource(R.string.ui_live), Modifier.align(Alignment.TopStart).padding(12.dp), color = CinemaTokens.LiveText, style = MaterialTheme.typography.labelMedium)
            }
            if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = CinemaTokens.Cyan)
            if (error) Text(stringResource(R.string.ui_preview_unavailable_try_fullscreen_playback), Modifier.fillMaxWidth().background(CinemaTokens.Background).padding(16.dp), color = CinemaTokens.Text)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(if (enabled) stringResource(R.string.ui_stop_preview) else stringResource(R.string.ui_start_preview), Modifier.testTag("live_preview_toggle"), onClick = toggle)
            Action(stringResource(R.string.ui_full_screen), Modifier.testTag("live_fullscreen"), primary = true) {
                // Release synchronously before the activity creates its player, even before recomposition.
                engine?.release(); engine = null; play(item)
            }
        }
    }
}

@Composable private fun GuidePanel(s: AppState, now: Long, retry: () -> Unit) {
    Text(stringResource(R.string.ui_on_this_channel), style = MaterialTheme.typography.titleMedium)
    if (s.liveGuideBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    else if (s.liveGuideError != null) ErrorState(s.liveGuideError, retry)
    else if (s.liveGuide.isEmpty()) ErrorState(stringResource(R.string.ui_no_schedule_supplied_for_this_channel_you_can_still_watch_live), retry)
    else {
        val current = GuideLayout.current(s.liveGuide, now)
        val next = s.liveGuide.firstOrNull { it.start > now }
        ProgrammeRow(stringResource(R.string.ui_now), current)
        current?.let { PlaybackProgress(GuideLayout.progress(it, now), Modifier.fillMaxWidth()) }
        ProgrammeRow(stringResource(R.string.ui_next), next)
        EPGTimeline(s.liveGuide, now)
    }
}

@Composable fun EPGTimeline(programmes: List<Programme>, now: Long) {
    val window = remember(programmes) { GuideLayout.window(programmes, now) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pixelsPerSecond = with(density) { 180.dp.toPx() } / GuideLayout.HALF_HOUR
    var selected by remember { mutableStateOf<Programme?>(null) }
    val jump: suspend () -> Unit = { scroll.scrollTo(((now - window.start) * pixelsPerSecond).toInt().coerceAtLeast(0)) }
    LaunchedEffect(window) { jump() }
    val nowDescription = stringResource(R.string.current_time, guideTime(now))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_program_guide), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            val scope = rememberCoroutineScope()
            CinemaTextButton({ scope.launch { jump() } }, Modifier.testTag("guide_now").focusRing()) { Text(stringResource(R.string.ui_jump_to_now)) }
        }
        Text(TimeZone.getDefault().displayName, color = Muted, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll).testTag("epg_timeline")) {
            val total = (window.duration.toFloat() / GuideLayout.HALF_HOUR * 180).dp
            Column(Modifier.width(total)) {
                Row {
                    var tick = window.start
                    while (tick < window.end) {
                        Text(guideTime(tick), Modifier.width(180.dp).padding(bottom = 8.dp), color = Muted, style = MaterialTheme.typography.labelMedium)
                        tick += GuideLayout.HALF_HOUR
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
                    programmes.filter { it.end > window.start && it.start < window.end }.forEach { programme ->
                        val start = window.fraction(programme.start)
                        val end = window.fraction(programme.end)
                        val current = programme.start <= now && now < programme.end
                        Surface(onClick = { selected = programme }, modifier = Modifier.offset(x = total * start).width((total * (end - start)).coerceAtLeast(1.dp)).padding(end = 4.dp).focusRing(),
                            color = if (current) MaterialTheme.colorScheme.primaryContainer else Panel, shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(programme.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Text("${guideTime(programme.start)} – ${guideTime(programme.end)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (now in window.start..window.end) Box(Modifier.offset(x = total * window.fraction(now)).width(2.dp).height(112.dp).background(CinemaTokens.Live)
                        .semantics { contentDescription = nowDescription })
                }
            }
        }
    }
    selected?.let { programme -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(programme.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("${guideTime(programme.start)} – ${guideTime(programme.end)}\n${TimeZone.getDefault().displayName}"); if (programme.description.isNotBlank()) Text(programme.description) } },
        confirmButton = { CinemaTextButton({ selected = null }, Modifier.focusRing()) { Text(stringResource(R.string.ui_close)) } }) }
}
private fun guideTime(seconds: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

@Composable fun PlaybackProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.height(4.dp), color = Sky, trackColor = MaterialTheme.colorScheme.surfaceVariant)
}
@Composable fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiLabel(message), color = Muted, style = MaterialTheme.typography.bodyMedium)
        CinemaTextButton(retry, Modifier.focusRing()) { Text(stringResource(R.string.ui_try_again)) }
    }
}
