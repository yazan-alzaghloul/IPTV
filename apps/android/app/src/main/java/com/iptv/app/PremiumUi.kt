package com.iptv.app

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.*

val LocalPosterFocus = compositionLocalOf { mutableStateOf<String?>(null) }

private fun Section.icon(): ImageVector = when (this) {
    Section.HOME -> Icons.Default.Home
    Section.LIVE -> CinemaIcons.LiveTv
    Section.MOVIES -> CinemaIcons.Movies
    Section.SERIES -> Icons.AutoMirrored.Filled.List
    Section.FAVORITES -> Icons.Default.Favorite
    Section.SEARCH -> Icons.Default.Search
    Section.SETTINGS -> Icons.Default.Settings
}
@Composable fun PremiumShell(vm: AppViewModel, play: (CatalogItem) -> Unit) {
    val s = vm.state
    val screens = rememberSaveableStateHolder()
    var signOut by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var sidebarFocused by remember { mutableStateOf(false) }
    val tv = LocalCinemaTv.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val touch = LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Touch
    val navigate: (Section) -> Unit = { if (touch) focus.clearFocus(); keyboard?.hide(); vm.selectSection(it) }
    val select: (CatalogItem) -> Unit = { focus.clearFocus(); keyboard?.hide(); vm.select(it) }
    BackHandler(s.section != Section.HOME) { navigate(Section.HOME) }
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text(stringResource(R.string.ui_sign_out_question)) },
        text = { Text(stringResource(R.string.ui_this_removes_your_saved_login_favorites_history_and_playback_progress_from_this_device)) },
        confirmButton = { CinemaTextButton({ signOut = false; vm.signOut() }, Modifier.focusRing()) { Text(stringResource(R.string.ui_sign_out)) } },
        dismissButton = { CinemaTextButton({ signOut = false }, Modifier.focusRing()) { Text(stringResource(R.string.ui_cancel)) } })
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val wide = tv || maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Box(Modifier.width(if (tv) 88.dp else 184.dp).fillMaxHeight().zIndex(2f)) {
                Column(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(if (tv && !sidebarFocused) 88.dp else if (tv) 224.dp else 184.dp)
                    .fillMaxHeight().background(Panel).onFocusChanged { sidebarFocused = it.hasFocus }.focusGroup()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (tv && !sidebarFocused) "◈" else "IPTV", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = AccentInk)
                    Section.entries.forEach { entry ->
                        val label = sectionTitle(entry)
                        NavigationDrawerItem(label = { if (!tv || sidebarFocused) Text(uiLabel(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = s.section == entry,
                            icon = { Icon(entry.icon(), if (tv && !sidebarFocused) label else null) }, onClick = { navigate(entry) }, modifier = Modifier.testTag("nav_${entry.name}").focusRing())
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (s.section == Section.HOME) stringResource(R.string.ui_your_next_great_watch) else sectionTitle(s.section),
                            style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (wide) Text(stringResource(R.string.ui_your_library_your_way), style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    if (s.section != Section.SEARCH) IconButton({ navigate(Section.SEARCH) }, Modifier.focusRing()) { Icon(Icons.Default.Search, stringResource(R.string.ui_search_library)) }
                    Box {
                        IconButton({ more = true }, Modifier.testTag("header_more").focusRing()) { Icon(Icons.Default.MoreVert, stringResource(R.string.ui_more_options)) }
                        if (more) MoreMenu({ more = false }, { more = false; navigate(it) }, { more = false; signOut = true })
                    }
                }
                Box(Modifier.weight(1f)) {
                    screens.SaveableStateProvider(s.section.name) {
                        val focusedPoster = rememberSaveable { mutableStateOf<String?>(null) }
                        CompositionLocalProvider(LocalPosterFocus provides focusedPoster) {
                        when (s.section) {
                            Section.HOME -> HomeDashboard(s, select, play = { if (it.kind == ContentKind.SERIES) select(it) else play(it) }, toggle = vm::toggle, retry = vm::refresh, featured = vm::loadFeatured)
                            Section.LIVE -> LiveTvScreen(vm, select, play)
                            Section.MOVIES, Section.SERIES -> VodExperience(vm, select, play)
                            Section.SETTINGS -> SettingsScreen(s, vm::appearance, vm::clearHistory)
                            else -> DiscoveryScreen(s, vm::search, vm::category, vm::sort, select, vm::toggle, vm::refresh, vm.vod, vm::rememberSearch)
                        }
                        }
                    }
                }
                if (!wide) NavigationBar(containerColor = Panel, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                    listOf(Section.HOME, Section.LIVE, Section.MOVIES, Section.SERIES).forEach { entry ->
                        NavigationBarItem(s.section == entry, { navigate(entry) }, icon = { Icon(entry.icon(), null) }, label = { Text(sectionTitle(entry), maxLines = 1) },
                            modifier = Modifier.testTag("nav_${entry.name}").focusRing())
                    }
                    NavigationBarItem(s.section in listOf(Section.FAVORITES, Section.SEARCH, Section.SETTINGS), { more = true },
                        icon = { Icon(Icons.Default.MoreVert, null) }, label = { Text(stringResource(R.string.ui_more)) }, modifier = Modifier.testTag("nav_LIBRARY").focusRing())
                }
            }
        }
    }
}

@Composable private fun MoreMenu(dismiss: () -> Unit, navigate: (Section) -> Unit, signOut: () -> Unit) {
    DropdownMenu(true, dismiss) {
        listOf(Section.FAVORITES, Section.SEARCH, Section.SETTINGS).forEach { entry ->
            DropdownMenuItem(text = { Text(sectionTitle(entry)) }, leadingIcon = { Icon(entry.icon(), null) },
                onClick = { navigate(entry) }, modifier = Modifier.testTag("nav_${entry.name}").focusRing())
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.ui_sign_out)) }, onClick = signOut, modifier = Modifier.testTag("sign_out").focusRing())
    }
}

private data class HomeCollections(val movies: List<CatalogItem> = emptyList(), val series: List<CatalogItem> = emptyList(), val live: List<CatalogItem> = emptyList(), val genres: Map<String, List<CatalogItem>> = emptyMap(), val recommended: List<CatalogItem> = emptyList())

@Composable private fun HomeDashboard(s: AppState, select: (CatalogItem) -> Unit, play: (CatalogItem) -> Unit,
    toggle: (CatalogItem) -> Unit, retry: () -> Unit, featured: (CatalogItem) -> Unit) {
    val favorites = remember(s.favorites) { s.favorites.map { it.key }.toSet() }
    val collections by produceState<HomeCollections?>(null, s.catalog, s.history, s.favorites) {
        value = withContext(Dispatchers.Default) {
            HomeCollections(s.catalog.filter { it.kind == ContentKind.MOVIE }.sortedByDescending { it.added },
                s.catalog.filter { it.kind == ContentKind.SERIES }.sortedByDescending { it.added },
                s.catalog.filter { it.kind == ContentKind.LIVE },
                s.catalog.asSequence().filter { it.kind != ContentKind.LIVE }.flatMap { item -> item.genre.orEmpty().split(',', '/', '|').map(String::trim).filter(String::isNotBlank).map { it to item } }
                    .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }.take(6).associate { it.key to it.value.take(20) },
                LocalRecommendations().recommend(s.catalog.filter { it.kind != ContentKind.LIVE }, s.history, s.favorites))
        }
    }
    val ready = collections
    if (ready == null) { LoadingSkeleton(); return }
    val movies = ready.movies
    val series = ready.series
    val live = ready.live
    val heroItems = remember(movies, series, live) { (movies.take(8) + series.take(8) + live.take(8)).take(8) }
    var featuredIndex by rememberSaveable { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val animationsAllowed = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0 }
    LaunchedEffect(s.appearance.heroRotation, s.appearance.motion, heroItems, heroFocused) {
        if (s.appearance.heroRotation && s.appearance.motion && animationsAllowed && !heroFocused && heroItems.size > 1)
            while (true) { delay(12_000); featuredIndex = (featuredIndex + 1) % heroItems.size }
    }
    val hero = heroItems.getOrNull(featuredIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
    LaunchedEffect(hero?.key) { hero?.takeIf { it.kind != ContentKind.LIVE }?.let(featured) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (s.busy && s.catalog.isEmpty()) item { LoadingTiles() }
        s.error?.let { message -> item { InlineFailure(message, retry) } }
        if (!s.busy && s.catalog.isEmpty() && s.error == null) item { EmptyLibrary("Your library is ready", "Refresh to load content from your provider.", retry) }
        heroItems.getOrNull(featuredIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))?.let { featuredItem -> item(key = "hero") {
            FeaturedHero(featuredItem, favorites.contains(featuredItem.key), { select(featuredItem) }, { play(featuredItem) }, { toggle(featuredItem) },
                Modifier.onFocusChanged { heroFocused = it.hasFocus }, s.featuredDetails.takeIf { s.featuredKey == featuredItem.key })
            if (heroItems.size > 1) Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CinemaTextButton({ featuredIndex = (featuredIndex - 1 + heroItems.size) % heroItems.size }, Modifier.focusRing()) { Text(stringResource(R.string.ui_previous_featured)) }
                Text("${featuredIndex + 1} / ${heroItems.size}", Modifier.padding(12.dp), color = Muted)
                CinemaTextButton({ featuredIndex = (featuredIndex + 1) % heroItems.size }, Modifier.focusRing()) { Text(stringResource(R.string.ui_next_featured)) }
            }
        } }
        val continuing = s.history.filter { (it.item.kind == ContentKind.MOVIE || it.item.parentSeriesId != null) && !it.completed && it.position > 0 }
        if (continuing.isNotEmpty() && "Continue watching" !in s.appearance.hiddenRows) item(key = "continue") {
            val entries = continuing.map { record ->
                val episode = record.item
                episode.parentSeriesId?.let { parent -> s.catalog.firstOrNull { it.kind == ContentKind.SERIES && it.id == parent }
                    ?: episode.copy(id = parent, name = episode.parentSeriesName ?: episode.name, parentSeriesId = null, parentSeriesName = null) } ?: episode
            }.distinctBy { it.key }
            val progress = continuing.reversed().associateBy { it.item.parentSeriesId?.let { parent -> "SERIES:$parent" } ?: it.item.key }
            MediaRow("Continue watching", entries, favorites, select, toggle, progress)
        }
        val recent = s.history.filter { it.item.kind == ContentKind.LIVE }.map { it.item }
        if (recent.isNotEmpty() && "Recently watched channels" !in s.appearance.hiddenRows) item(key = "recent") {
            MediaRow("Recently watched channels", recent, favorites, select, toggle)
        }
        if (s.favorites.isNotEmpty() && "Favorites" !in s.appearance.hiddenRows) item(key = "favorites") {
            MediaRow("Favorites", s.favorites, favorites, select, toggle)
        }
        if (live.isNotEmpty() && "Live TV" !in s.appearance.hiddenRows) item(key = "live") { MediaRow("Live TV", live, favorites, select, toggle) }
        if (movies.isNotEmpty() && "Movies" !in s.appearance.hiddenRows) item(key = "movies") { MediaRow(if (movies.any { it.added > 0 }) "Recently added movies" else "Movies", movies, favorites, select, toggle) }
        if (series.isNotEmpty() && "Series" !in s.appearance.hiddenRows) item(key = "series") { MediaRow("Series", series, favorites, select, toggle) }
        if (ready.recommended.isNotEmpty()) item(key = "recommended") { MediaRow("Recommended from your viewing", ready.recommended, favorites, select, toggle) }
        items(ready.genres.entries.toList(), key = { "genre:${it.key}" }) { (genre, entries) -> MediaRow(genre, entries, favorites, select, toggle) }
        item { CinemaTextButton(retry, Modifier.padding(horizontal = 16.dp).focusRing()) { Text(stringResource(R.string.ui_refresh_library)) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FeaturedHero(item: CatalogItem, favorite: Boolean, details: () -> Unit, play: () -> Unit, toggle: () -> Unit, modifier: Modifier = Modifier, info: ContentDetails? = null, playLabel: String? = null) {
    val kindLabel = uiLabel(item.kind.label)
    BoxWithConstraints(modifier.focusGroup().fillMaxWidth().background(CinemaTokens.Background)) {
        val wide = maxWidth > 600.dp
        val height = if (wide) 400.dp else 410.dp
        AsyncImage(info?.backdrop?.takeIf { it.isNotBlank() } ?: item.backdrop?.takeIf { it.isNotBlank() } ?: item.image, contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(CinemaTokens.Background.copy(alpha = if (wide) 0.96f else 0.65f), CinemaTokens.Background.copy(alpha = 0.25f)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, CinemaTokens.Background.copy(alpha = 0.85f), CinemaTokens.Background), startY = 0f)))
        Column(Modifier.fillMaxWidth(if (wide) 0.75f else 1f).heightIn(min = height).padding(horizontal = if (wide) 32.dp else 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.Bottom) {
            Text(stringResource(R.string.ui_featured_in_your_library), color = CinemaTokens.Cyan, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            Text(item.name, color = CinemaTokens.Text, style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.year ?: info?.year?.takeIf { it.toIntOrNull()?.let { y -> y > 1800 } == true }, item.genre ?: info?.genre?.takeIf { it.isNotBlank() }, info?.duration?.takeIf { it.isNotBlank() }, info?.classification?.takeIf { it.isNotBlank() }, info?.episodes?.map { it.season }?.distinct()?.size?.takeIf { it > 0 }?.let { stringResource(R.string.season_count, it) }, (item.rating ?: info?.rating)?.takeIf { it.toDoubleOrNull()?.let { n -> n > 0 } == true }?.let { stringResource(R.string.rating_value, it) }).ifEmpty { listOf(kindLabel) }.joinToString("  ·  "),
                color = CinemaTokens.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
            (info?.description?.takeIf { it.isNotBlank() } ?: item.overview)?.let { overview ->
                Text(overview, color = CinemaTokens.Text.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(play, Modifier.heightIn(min = 48.dp).focusRing(), shape = CinemaTokens.PosterShape) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(playLabel ?: if (item.kind == ContentKind.SERIES) stringResource(R.string.ui_view_episodes) else stringResource(R.string.ui_play_now)) }
                OutlinedButton(details, Modifier.heightIn(min = 48.dp).focusRing(), shape = CinemaTokens.PosterShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_more_details)) }
                IconButton(toggle, Modifier.size(48.dp).background(CinemaTokens.Background.copy(alpha = 0.7f), CinemaTokens.PosterShape).focusRing()) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.Add, if (favorite) "Remove from favorites" else "Add to favorites", tint = if (favorite) CinemaTokens.AccentText else Color.White) }
            }
        }
    }
}

@Composable private fun MediaRow(title: String, items: List<CatalogItem>, favorites: Set<String>, select: (CatalogItem) -> Unit,
    toggle: (CatalogItem) -> Unit, progress: Map<String, WatchRecord> = emptyMap()) {
    Column(Modifier.testTag("rail_$title"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiLabel(title), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        PosterRail(items.take(20), favorites, progress, select, toggle, title)
    }
}

@Composable private fun DiscoveryScreen(s: AppState, search: (String) -> Unit, category: (String) -> Unit, sort: (CatalogSort) -> Unit,
    select: (CatalogItem) -> Unit, toggle: (CatalogItem) -> Unit, retry: () -> Unit, store: VodCatalogStore, rememberSearch: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var sorting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speech = remember { android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
    val canSpeak = remember { speech.resolveActivity(context.packageManager) != null }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.take(240)?.let { search(it); rememberSearch(it) }
    }
    val favorites = remember(s.favorites) { s.favorites.map { it.key }.toSet() }
    val history = remember(s.history) { s.history.associateBy { it.item.key } }
    var savedType by rememberSaveable(s.section.name) { mutableStateOf("ALL") }
    val results by produceState<List<CatalogItem>?>(null, s.catalog, s.search, s.category, s.sort, s.vodRevision, savedType) {
        if (s.search.isNotBlank()) delay(180)
        val source = if (s.section == Section.SEARCH) withContext(Dispatchers.IO) {
            val sort = when (s.sort) { CatalogSort.NAME -> VodSort.TITLE; CatalogSort.RATING -> VodSort.RATING; else -> VodSort.ADDED }
            val filter = VodFilter(query = s.search, sort = sort)
            store.page(s.accountId, ContentKind.MOVIE, filter, limit = 100).items + store.page(s.accountId, ContentKind.SERIES, filter, limit = 100).items +
                withContext(Dispatchers.Default) { CatalogQuery.apply(s.catalog.filter { it.kind == ContentKind.LIVE }, s.search).take(100) }
        } else withContext(Dispatchers.Default) { CatalogQuery.apply(s.catalog, s.search, s.category, s.sort) }
        value = if (savedType == "ALL") source else source.filter { it.kind.name == savedType }
    }
    val shown = results.orEmpty()
    val grid = rememberLazyGridState()
    var lastFocus by rememberSaveable(s.section.name) { mutableStateOf<String?>(null) }
    val requester = remember { FocusRequester() }
    val input = LocalInputModeManager.current
    LaunchedEffect(s.section) {
        if (lastFocus != null && input.inputMode == androidx.compose.ui.input.InputMode.Keyboard) {
            delay(150); runCatching { requester.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(s.search, { search(it.take(240)) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (s.section == Section.SEARCH) stringResource(R.string.ui_search_your_library) else stringResource(R.string.search_kind, sectionTitle(s.section).lowercase())) },
            leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { Row {
                if (s.search.isNotBlank()) CinemaTextButton({ search("") }) { Text(stringResource(R.string.ui_clear)) }
                if (s.section == Section.SEARCH && canSpeak) CinemaTextButton({ voice.launch(speech) }) { Text(stringResource(R.string.ui_voice)) }
            } },
            shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { rememberSearch(s.search); keyboard?.hide(); focus.clearFocus() }), colors = cinemaFieldColors())
        if (s.section == Section.SEARCH && s.search.isBlank() && s.recentSearches.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.recentSearches) { recent -> SuggestionChip({ search(recent) }, { Text(recent) }, modifier = Modifier.focusRing()) }
        }
        if (s.section in listOf(Section.FAVORITES, Section.SEARCH)) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("ALL" to "All", "MOVIE" to "Movies", "SERIES" to "Series", "LIVE" to "Channels")) { (key, label) ->
                FilterChip(savedType == key, { savedType = key }, { Text(uiLabel(label)) }, modifier = Modifier.focusRing())
            }
        }
        if (s.categories.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(s.category.isEmpty(), { category("") }, { Text(stringResource(R.string.ui_all_categories)) }, modifier = Modifier.focusRing()) }
            items(s.categories, key = { it.id }) { c -> FilterChip(s.category == c.id, { category(c.id) }, { Text(c.name) }, modifier = Modifier.focusRing()) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(if (s.section == Section.LIVE) R.plurals.channel_count else R.plurals.title_count, shown.size, shown.size), Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
            Box {
                CinemaTextButton({ sorting = true }, Modifier.focusRing()) { Text(uiLabel(s.sort.label)) }
                DropdownMenu(sorting, { sorting = false }) {
                    CatalogSort.entries.filter { it != CatalogSort.RATING || s.catalog.any { item -> item.rating != null } }.forEach { choice ->
                        DropdownMenuItem(text = { Text(uiLabel(choice.label)) }, onClick = { sorting = false; sort(choice) })
                    }
                }
            }
            CinemaTextButton(retry, Modifier.focusRing()) { Text(stringResource(R.string.ui_refresh)) }
        }
        s.error?.let { InlineFailure(it, retry) }
        if (s.busy && s.catalog.isEmpty()) LoadingTiles()
        else if (results == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (shown.isEmpty()) EmptyLibrary(if (s.search.isNotBlank()) "No matching titles" else if (s.section == Section.FAVORITES) "Keep your favorites here" else "No titles available",
            if (s.section == Section.FAVORITES) "Open a title and add it to favorites. Long-press a card to save it quickly." else "Try another search or category.", retry)
        else LazyVerticalGrid(GridCells.Adaptive(if (s.section == Section.LIVE) 220.dp else 145.dp), Modifier.weight(1f), state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val groups = if (s.section == Section.SEARCH) shown.groupBy { it.kind } else mapOf<ContentKind?, List<CatalogItem>>(null to shown)
            groups.forEach { (kind, entries) ->
                if (kind != null) item(span = { GridItemSpan(maxLineSpan) }) { Text(kind.label, style = MaterialTheme.typography.titleLarge) }
                items(entries, key = { it.key }) { item ->
                    PremiumCard(item, favorites.contains(item.key), history[item.key],
                        Modifier.then(if (item.key == lastFocus) Modifier.focusRequester(requester) else Modifier).onFocusChanged { if (it.isFocused) lastFocus = item.key },
                        { select(item) }, { toggle(item) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun PremiumCard(item: CatalogItem, favorite: Boolean, progress: WatchRecord?, modifier: Modifier = Modifier, select: () -> Unit, toggle: () -> Unit) {
    val kindLabel = uiLabel(item.kind.label)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, tween(if (LocalCinemaMotion.current) 140 else 0), label = "Poster focus")
    Column(modifier.testTag("card_${item.key}").graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused }.focusRing()
        .clip(CinemaTokens.PosterShape).combinedClickable(onClick = select, onLongClick = toggle,
            onLongClickLabel = if (favorite) "Remove from favorites" else "Add to favorites")
        .semantics(mergeDescendants = true) {
            customActions = listOf(CustomAccessibilityAction(if (favorite) "Remove from favorites" else "Add to favorites") { toggle(); true })
        }) {
        Box {
            Artwork(item, Modifier.fillMaxWidth().aspectRatio(if (item.kind == ContentKind.LIVE) 16f / 9 else 2f / 3))
            if (favorite) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).background(CinemaTokens.Background.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                Icon(Icons.Default.Favorite, stringResource(R.string.ui_favorite), Modifier.size(18.dp), tint = CinemaTokens.AccentText)
            }
            if (item.kind == ContentKind.LIVE) Text(stringResource(R.string.ui_live), Modifier.align(Alignment.BottomStart).padding(8.dp).background(CinemaTokens.Background.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = CinemaTokens.LiveText, style = MaterialTheme.typography.labelSmall)
            if (progress != null && progress.duration > 0 && item.kind != ContentKind.LIVE)
                PlaybackProgress(progress.position.toFloat() / progress.duration, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  ").ifEmpty { kindLabel }, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Save both the rail position and the focused item when details temporarily replace the shell. */
@Composable fun PosterRail(items: List<CatalogItem>, favorites: Set<String>, records: Map<String, WatchRecord>, select: (CatalogItem) -> Unit, toggle: (CatalogItem) -> Unit, railId: String) {
    var lastFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val state = rememberLazyListState()
    val requester = remember { FocusRequester() }
    val input = LocalInputModeManager.current
    val focusedPoster = LocalPosterFocus.current
    LaunchedEffect(Unit) {
        if (lastFocus != null && focusedPoster.value == "$railId:$lastFocus" && input.inputMode == androidx.compose.ui.input.InputMode.Keyboard) {
            val index = items.indexOfFirst { it.key == lastFocus }
            if (index >= 0) { state.scrollToItem(index); withFrameNanos { }; runCatching { requester.requestFocus() } }
        }
    }
    LazyRow(state = state, modifier = Modifier.focusGroup(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items, key = { it.key }) { item ->
            PremiumCard(item, item.key in favorites, records[item.key],
                Modifier.width(if (item.kind == ContentKind.LIVE) 216.dp else if (LocalCinemaTv.current) 172.dp else 152.dp)
                    .then(if (item.key == lastFocus) Modifier.focusRequester(requester) else Modifier)
                    .onFocusChanged { if (it.isFocused) { lastFocus = item.key; focusedPoster.value = "$railId:${item.key}" } }, { focusedPoster.value = "$railId:${item.key}"; lastFocus = item.key; select(item) }, { toggle(item) })
        }
    }
}
@Composable private fun LoadingTiles() = LoadingSkeleton()

@Composable fun LoadingSkeleton() {
    Column(Modifier.fillMaxWidth().padding(24.dp).semantics { contentDescription = "Loading library" }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { Box(Modifier.weight(1f).aspectRatio(2f / 3).clip(CinemaTokens.PosterShape).background(Panel)) }
        }
    }
}
@Composable private fun EmptyLibrary(title: String, description: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiLabel(title), style = MaterialTheme.typography.titleLarge); Text(uiLabel(description), color = Muted); Action(stringResource(R.string.ui_refresh_library), onClick = retry)
    }
}
@Composable private fun InlineFailure(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ErrorMessage(message); CinemaTextButton(retry, Modifier.focusRing()) { Text(stringResource(R.string.ui_try_again)) }
    }
}
