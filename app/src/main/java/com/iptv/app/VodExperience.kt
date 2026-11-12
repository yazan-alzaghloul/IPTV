package com.iptv.app

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

private data class VodRail(val title: String, val items: List<CatalogItem>)
private data class VodLanding(val featured: List<CatalogItem> = emptyList(), val rails: List<VodRail> = emptyList())

@OptIn(ExperimentalLayoutApi::class)
@Composable fun VodExperience(vm: AppViewModel, select: (CatalogItem) -> Unit, play: (CatalogItem) -> Unit) {
    val s = vm.state
    val kind = s.section.kind ?: return
    var browse by rememberSaveable(kind.name) { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    BackHandler(browse) { browse = false }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(if (browse) stringResource(R.string.discover_kind, uiLabel(kind.label).lowercase()) else stringResource(R.string.browse_kind, uiLabel(kind.label).lowercase()), primary = true) { browse = !browse }
            Action(stringResource(R.string.ui_watch_history)) { history = true }
        }
        if (history) WatchHistoryDialog(s, { history = false }, vm::removeHistory, vm::clearHistory, select)
        if (browse) VodBrowser(vm, kind, select)
        else {
            val landing by produceState<VodLanding?>(null, s.accountId, s.vodRevision, kind, s.history, s.favorites) {
                value = withContext(Dispatchers.IO) {
                    val recent = vm.vod.page(s.accountId, kind, limit = 100).items
                    val groups = vm.vod.categories(s.accountId, kind)
                    val facets = vm.vod.facets(s.accountId, kind)
                    val continuing = if (kind == ContentKind.MOVIE) s.history.filter { it.item.kind == kind && it.position > 0 && !it.completed }.map { it.item }
                    else s.history.filter { it.item.parentSeriesId != null }.mapNotNull {
                        vm.vod.find(s.accountId, ContentKind.SERIES, it.item.parentSeriesId!!)
                    }.distinctBy { it.key }
                    val rails = buildList {
                        if (continuing.isNotEmpty()) add(VodRail(if (kind == ContentKind.MOVIE) "Continue watching" else "Continue watching series", continuing))
                        val saved = s.favorites.filter { it.kind == kind }
                        if (saved.isNotEmpty()) add(VodRail("My watchlist", saved))
                        if (recent.isNotEmpty()) add(VodRail("Featured ${kind.label.lowercase()}", recent.take(12)))
                        if (recent.any { it.added > 0 }) add(VodRail("Recently added", recent.take(12)))
                        val candidates = recent + groups.take(6).flatMap { vm.vod.page(s.accountId, kind, VodFilter(category = it.id), limit = 20).items }
                        val recommendations = LocalRecommendations().recommend(candidates.distinctBy { it.key }, s.history, s.favorites)
                        if (recommendations.isNotEmpty()) add(VodRail("Recommended from your viewing", recommendations))
                        if (facets.hasRatings) add(VodRail("Top rated by your provider", vm.vod.page(s.accountId, kind, VodFilter(ratedOnly = true, sort = VodSort.RATING), limit = 12).items))
                        groups.take(8).forEach { group ->
                            val entries = vm.vod.page(s.accountId, kind, VodFilter(category = group.id), limit = 12).items
                            if (entries.isNotEmpty()) add(VodRail(group.name, entries))
                        }
                    }
                    VodLanding(recent.take(8), rails)
                }
            }
            var featured by rememberSaveable(kind.name) { mutableIntStateOf(0) }
            var heroFocus by remember { mutableStateOf(false) }
            val heroItems = landing?.featured.orEmpty()
            val hero = heroItems.getOrNull(featured.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
            LaunchedEffect(hero?.key) { hero?.let(vm::loadFeatured) }
            val context = LocalContext.current
            val animationsAllowed = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0 }
            LaunchedEffect(heroItems, s.appearance.heroRotation, s.appearance.motion, heroFocus) {
                if (s.appearance.heroRotation && s.appearance.motion && animationsAllowed && !heroFocus && heroItems.size > 1)
                    while (true) { delay(12_000); featured = (featured + 1) % heroItems.size }
            }
            if (landing == null) LoadingSkeleton() else LazyColumn(Modifier.fillMaxSize().testTag("vod_landing"), verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                if (s.error != null) item { VodError(s.error, vm::refresh) }
                if (hero == null && s.busy) item { VodSkeleton() }
                if (hero == null && !s.busy) item { Text(stringResource(R.string.no_kind, uiLabel(kind.label).lowercase()), Modifier.padding(24.dp), color = Muted) }
                if (hero != null) item(key = "featured") {
                    Crossfade(hero, animationSpec = tween(if (s.appearance.motion && animationsAllowed) 300 else 0), label = "Featured title") { title ->
                        val info = s.featuredDetails.takeIf { s.featuredKey == title.key }
                        val continuation = if (kind == ContentKind.SERIES) SeriesProgress.continuation(title.id, info?.episodes.orEmpty(), s.history, s.watched) else null
                        FeaturedHero(title, s.favorites.any { it.key == title.key }, { select(title) },
                            { if (kind == ContentKind.MOVIE) play(title.copy(extension = info?.extension ?: title.extension)) else if (continuation != null) play(continuation.episode.item) else select(title) },
                            { vm.toggle(title) }, Modifier.onFocusChanged { heroFocus = it.hasFocus }, info,
                            if (kind == ContentKind.SERIES) { if (continuation?.resume == true) "Continue series" else if (continuation != null) "Watch now" else "View episodes" } else if (s.history.any { it.item.key == title.key && it.position > 0 && !it.completed }) "Resume watching" else "Play now")
                    }
                    if (heroItems.size > 1) Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CinemaTextButton({ featured = (featured - 1 + heroItems.size) % heroItems.size }, Modifier.focusRing()) { Text(stringResource(R.string.ui_previous_featured)) }
                        Text("${featured + 1} / ${heroItems.size}", Modifier.padding(12.dp), color = Muted)
                        CinemaTextButton({ featured = (featured + 1) % heroItems.size }, Modifier.focusRing()) { Text(stringResource(R.string.ui_next_featured)) }
                    }
                }
                items(landing?.rails.orEmpty(), key = { it.title }) { rail -> ContentCarousel(rail.title, rail.items, s, select, vm::toggle) }
                if (hero != null) item { Action(stringResource(R.string.all_kind, uiLabel(kind.label).lowercase()), Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { browse = true } }
            }
        }
    }
}

@Composable fun ContentCarousel(title: String, items: List<CatalogItem>, s: AppState, select: (CatalogItem) -> Unit, toggle: (CatalogItem) -> Unit) {
    Column(Modifier.testTag("rail_$title"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(uiLabel(title), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val favorites = remember(s.favorites) { s.favorites.map { it.key }.toSet() }
        val records = remember(s.history) { s.history.reversed().associateBy { it.item.parentSeriesId?.let { parent -> "SERIES:$parent" } ?: it.item.key } }
        PosterRail(items, favorites, records, select, toggle, title)
    }
}
@Composable fun MoviePosterCard(item: CatalogItem, favorite: Boolean, record: WatchRecord?, modifier: Modifier = Modifier, select: () -> Unit, toggle: () -> Unit) = PremiumCard(item, favorite, record, modifier, select, toggle)
@Composable fun SeriesPosterCard(item: CatalogItem, favorite: Boolean, record: WatchRecord?, modifier: Modifier = Modifier, select: () -> Unit, toggle: () -> Unit) = PremiumCard(item, favorite, record, modifier, select, toggle)
@Composable fun ContinueWatchingCard(item: CatalogItem, favorite: Boolean, record: WatchRecord?, modifier: Modifier, select: () -> Unit, toggle: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.kind == ContentKind.MOVIE) MoviePosterCard(item, favorite, record, select = select, toggle = toggle)
        else SeriesPosterCard(item, favorite, record, select = select, toggle = toggle)
        if (record?.item?.parentSeriesId != null) Text(stringResource(R.string.episode_code, record.item.seasonNumber ?: 0, record.item.episodeNumber ?: 0), color = AccentInk, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun VodBrowser(vm: AppViewModel, kind: ContentKind, select: (CatalogItem) -> Unit) {
    val s = vm.state
    var lastFocus by rememberSaveable(kind.name) { mutableStateOf<String?>(null) }
    var restoreFocus by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val input = LocalInputModeManager.current
    var query by rememberSaveable(kind.name) { mutableStateOf("") }
    var category by rememberSaveable(kind.name) { mutableStateOf("") }
    var year by rememberSaveable(kind.name) { mutableStateOf("") }
    var language by rememberSaveable(kind.name) { mutableStateOf("") }
    var genre by rememberSaveable(kind.name) { mutableStateOf("") }
    var sorting by rememberSaveable(kind.name) { mutableStateOf(VodSort.ADDED.name) }
    var page by rememberSaveable(kind.name) { mutableIntStateOf(0) }
    var list by rememberSaveable(kind.name) { mutableStateOf(false) }
    val filter = VodFilter(query, category, year, language, genre, sort = VodSort.valueOf(sorting))
    var appliedFilter by rememberSaveable(kind.name) { mutableStateOf(filter.toString()) }
    LaunchedEffect(filter) { if (appliedFilter != filter.toString()) { page = 0; appliedFilter = filter.toString() } }
    val data by produceState<VodPage?>(null, s.accountId, s.vodRevision, filter, page) {
        if (query.isNotBlank()) delay(180)
        value = withContext(Dispatchers.IO) { vm.vod.page(s.accountId, kind, filter, page * 48) }
    }
    LaunchedEffect(data, list) {
        val entries = data?.items ?: return@LaunchedEffect
        if (restoreFocus) {
            restoreFocus = false
            val index = entries.indexOfFirst { it.key == lastFocus }
            if (index >= 0 && input.inputMode == androidx.compose.ui.input.InputMode.Keyboard) {
                if (list) listState.scrollToItem(index) else gridState.scrollToItem(index)
                withFrameNanos { }; runCatching { focusRequester.requestFocus() }
            }
        }
    }
    val facets by produceState(VodFacets(), s.accountId, s.vodRevision) { value = withContext(Dispatchers.IO) { vm.vod.facets(s.accountId, kind) } }
    val categories by produceState<List<Category>>(emptyList(), s.accountId, s.vodRevision) { value = withContext(Dispatchers.IO) { vm.vod.categories(s.accountId, kind) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it.take(240); vm.rememberSearch(query) }
    }
    val speech = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
    val canSpeak = remember { speech.resolveActivity(context.packageManager) != null }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(query, { query = it.take(240) }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(16.dp),
            label = { Text(stringResource(R.string.search_kind, uiLabel(kind.label).lowercase())) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { Row { if (query.isNotBlank()) CinemaTextButton({ query = "" }) { Text(stringResource(R.string.ui_clear)) }; if (canSpeak) CinemaTextButton({ voice.launch(speech) }) { Text(stringResource(R.string.ui_voice)) } } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query); keyboard?.hide(); focus.clearFocus() }), colors = cinemaFieldColors())
        IconButton({ list = !list }, Modifier.size(48.dp).testTag("vod_view_toggle").focusRing()) { Icon(Icons.Default.Menu, if (list) "Grid view" else "List view") }
        IconButton(vm::refresh, Modifier.size(48.dp).testTag("vod_refresh").focusRing()) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (query.isBlank() && s.recentSearches.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.recentSearches) { recent -> SuggestionChip({ query = recent }, { Text(recent) }, modifier = Modifier.focusRing()) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (categories.isNotEmpty()) item { VodFilterMenu("Category", categories.map { it.id to it.name }, category) { category = it } }
            if (facets.genres.isNotEmpty()) item { VodFilterMenu("Genre", facets.genres.map { it to it }, genre) { genre = it } }
            if (facets.years.isNotEmpty()) item { VodFilterMenu("Year", facets.years.map { it to it }, year) { year = it } }
            if (facets.languages.isNotEmpty()) item { VodFilterMenu("Language", facets.languages.map { it to it }, language) { language = it } }
            item { VodFilterMenu("Sort", VodSort.entries.filter { it != VodSort.RATING || facets.hasRatings }.map { it.name to it.label }, sorting, includeAll = false) { sorting = it } }
        }
        s.error?.let { VodError(it, vm::refresh) }
        val result = data
        if (result == null || (s.busy && result.total == 0)) VodSkeleton()
        else if (result.items.isEmpty()) Column(Modifier.padding(16.dp)) {
            Text(if (query.isNotBlank()) "No matching titles" else "No titles available", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_try_another_filter_or_refresh_your_provider), color = Muted)
        }
        else {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(stringResource(R.string.ui_previous_page), enabled = page > 0) { page-- }
                Text(stringResource(R.string.page_range, result.offset + 1, result.offset + result.items.size, result.total), Modifier.padding(12.dp), color = Muted)
                Action(stringResource(R.string.ui_next_page), enabled = result.offset + result.items.size < result.total) { page++ }
            }
            key(kind, page, filter) {
                if (list) LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(result.items, key = { it.key }) { item ->
                        OutlinedCard({ select(item) }, Modifier.fillMaxWidth().testTag("card_${item.key}").then(if (lastFocus == item.key) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { if (it.isFocused) lastFocus = item.key }.focusRing()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Artwork(item, Modifier.width(80.dp).height(110.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text(listOfNotNull(item.year, item.genre, item.language).joinToString(" · "), color = Muted)
                                    CinemaTextButton({ vm.toggle(item) }) { Text(if (s.favorites.any { it.key == item.key }) stringResource(R.string.ui_remove_from_watchlist) else stringResource(R.string.ui_add_to_watchlist)) }
                                }
                            }
                        }
                    }
                } else LazyVerticalGrid(GridCells.Adaptive(if (LocalCinemaTv.current) 160.dp else 145.dp), Modifier.weight(1f), state = gridState, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(result.items, key = { it.key }) { item ->
                        PremiumCard(item, s.favorites.any { it.key == item.key }, s.history.firstOrNull { it.item.key == item.key }, Modifier.then(if (lastFocus == item.key) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { if (it.isFocused) lastFocus = item.key }, select = { select(item) }, toggle = { vm.toggle(item) })
                    }
                }
            }
        }
    }
}
@Composable fun GenreChip(label: String, selected: Boolean, click: () -> Unit) = FilterChip(selected, click, { Text(uiLabel(label)) }, modifier = Modifier.focusRing())
@Composable private fun VodFilterMenu(label: String, values: List<Pair<String,String>>, current: String, includeAll: Boolean = true, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Action(if (current.isBlank()) label else "$label: ${values.firstOrNull { it.first == current }?.second ?: current}") { open = true }
        DropdownMenu(open, { open = false }) {
            if (includeAll) DropdownMenuItem({ Text(stringResource(R.string.ui_all)) }, { change(""); open = false })
            values.forEach { (key, value) -> DropdownMenuItem({ Text(value) }, { change(key); open = false }) }
        }
    }
}
@Composable fun WatchHistoryDialog(s: AppState, dismiss: () -> Unit, remove: (CatalogItem) -> Unit, clear: () -> Unit, select: (CatalogItem) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.ui_watch_history)) }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.history.isEmpty()) item { Text(stringResource(R.string.ui_your_watch_history_is_empty)) }
            items(s.history, key = { it.item.key }) { record ->
                Column {
                    Text(record.item.parentSeriesName?.let { "$it · ${record.item.name}" } ?: record.item.name)
                    Text(if (record.completed) "Watched" else "${record.position / 60_000} min", color = Muted)
                    CinemaTextButton({ remove(record.item) }) { Text(stringResource(R.string.ui_remove_history_progress)) }
                }
            }
        }
    }, confirmButton = { CinemaTextButton(dismiss) { Text(stringResource(R.string.ui_close)) } }, dismissButton = { CinemaTextButton(clear, enabled = s.history.isNotEmpty()) { Text(stringResource(R.string.ui_clear_history)) } })
}
@Composable private fun VodError(message: String, retry: () -> Unit) { Column(Modifier.padding(horizontal = 20.dp)) { ErrorMessage(message); CinemaTextButton(retry) { Text(stringResource(R.string.ui_try_again)) } } }
@Composable fun VodSkeleton() = LoadingSkeleton()
