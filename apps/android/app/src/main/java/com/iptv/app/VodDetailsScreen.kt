package com.iptv.app

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun VodDetailsScreen(vm: AppViewModel, back: () -> Unit, play: (CatalogItem, Boolean) -> Unit) {
    val s = vm.state
    val item = s.selected ?: return
    val info = s.details
    val context = LocalContext.current
    val episodes = info?.episodes.orEmpty()
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val continuation = if (item.kind == ContentKind.SERIES) SeriesProgress.continuation(item.id, episodes, s.history, s.watched) else null
    val activeSeason = s.selectedSeason?.takeIf { it in seasons } ?: continuation?.episode?.season ?: seasons.firstOrNull()
    val progress = s.history.firstOrNull { it.item.key == item.key }
    var technical by rememberSaveable(item.key) { mutableStateOf(false) }
    val related by produceState<List<CatalogItem>>(emptyList(), item.key, s.vodRevision) {
        value = withContext(Dispatchers.IO) { vm.vod.page(s.accountId, item.kind, VodFilter(category = item.categoryId), limit = 13).items.filterNot { it.key == item.key }.take(12) }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("vod_details"), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Action(stringResource(R.string.back_to, sectionTitle(s.section)), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onClick = back) }
        item { ContentDetailsHeader(item, info) }
        item {
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.kind == ContentKind.MOVIE) {
                    Action(if (progress != null && progress.position > 0 && !progress.completed) stringResource(R.string.ui_resume_watching) else stringResource(R.string.ui_play_movie), Modifier.testTag("vod_play"), primary = true) { play(item, false) }
                    if (progress != null && progress.position > 0) Action(stringResource(R.string.ui_restart_movie)) { play(item, true) }
                } else if (continuation != null) {
                    val episode = continuation.episode
                    Text(if (continuation.resume) stringResource(R.string.continue_episode, episode.season, episode.number) else if (continuation.next) stringResource(R.string.next_episode_heading, episode.season, episode.number) else stringResource(R.string.start_episode_heading, episode.season, episode.number), color = AccentInk)
                    Action(if (continuation.resume) stringResource(R.string.ui_continue_series) else stringResource(R.string.ui_watch_now), Modifier.testTag("continue_series"), primary = true) { play(episode.item, false) }
                }
                Action(if (s.favorites.any { it.key == item.key }) stringResource(R.string.ui_remove_from_watchlist) else stringResource(R.string.ui_add_to_watchlist), Modifier.testTag("vod_watchlist")) { vm.toggle(item) }
                if (info?.trailer != null) Action(stringResource(R.string.ui_watch_trailer)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.trailer))) }
                        .onFailure { Toast.makeText(context, localizedUiLabel(context, "No app is available to open this trailer."), Toast.LENGTH_SHORT).show() }
                }
            }
        }
        if (s.detailBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
        s.detailError?.let { error -> item { Column(Modifier.padding(horizontal = 20.dp)) { ErrorMessage(error); Action(stringResource(R.string.ui_retry_details)) { vm.select(item) } } } }
        item { Text(info?.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ui_description_unavailable), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge, color = if (info?.description.isNullOrBlank()) Muted else MaterialTheme.colorScheme.onSurface) }
        if (!info?.cast.isNullOrBlank()) item { DetailFact(stringResource(R.string.ui_cast), info!!.cast) }
        if (!info?.director.isNullOrBlank()) item { DetailFact(stringResource(R.string.ui_director), info!!.director) }
        if (!info?.language.isNullOrBlank()) item { DetailFact(stringResource(R.string.ui_provider_languages), info!!.language) }
        if (!info?.subtitles.isNullOrBlank()) item { DetailFact(stringResource(R.string.ui_provider_subtitles), info!!.subtitles) }
        if (item.kind == ContentKind.SERIES) {
            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { season -> GenreChip(stringResource(R.string.season_number, season), activeSeason == season) { vm.saveSeason(item.id, season) } }
                    }
                }
                item {
                    val selected = episodes.filter { it.season == activeSeason }
                    val allWatched = selected.all { it.item.key in s.watched }
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action(if (allWatched) stringResource(R.string.ui_mark_season_unwatched) else stringResource(R.string.ui_mark_season_watched)) { vm.markWatched(selected.map { it.item }, !allWatched) }
                        var autoplay by remember { mutableStateOf(PlaybackPreferences(context).autoplay) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_autoplay_next_episode), Modifier.weight(1f))
                            Switch(autoplay, { autoplay = it; PlaybackPreferences(context).autoplay = it }, Modifier.focusRing())
                        }
                    }
                }
                items(episodes.filter { it.season == activeSeason }, key = { it.item.key }) { episode ->
                    EpisodeCard(episode, s.history.firstOrNull { it.item.key == episode.item.key }, episode.item.key in s.watched,
                        play = { play(episode.item, false) }, restart = { play(episode.item, true) }, mark = { vm.markWatched(listOf(episode.item), episode.item.key !in s.watched) })
                }
            } else if (!s.detailBusy) item { Text(stringResource(R.string.ui_no_episodes_available_from_this_provider), Modifier.padding(20.dp), color = Muted) }
        }
        if (related.isNotEmpty()) item { ContentCarousel(stringResource(if (item.kind == ContentKind.MOVIE) R.string.related_movies else R.string.related_series), related, s, vm::select, vm::toggle) }
        if (info != null) item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(uiLabel(if (technical) "Hide streaming information" else "Streaming information")) { technical = !technical }
                if (technical) {
                    if (info.technical.isNotBlank()) Text(stringResource(R.string.provider_video, info.technical), color = Muted)
                    Text(stringResource(R.string.provider_container, info.extension), color = Muted)
                }
            }
        }
    }
}
@Composable fun ContentDetailsHeader(item: CatalogItem, info: ContentDetails?) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(CinemaTokens.Background)) {
        val wide = maxWidth >= 600.dp
        AsyncImage(info?.backdrop?.takeIf { it.isNotBlank() } ?: item.backdrop ?: item.image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(CinemaTokens.Background.copy(alpha = 0.9f), CinemaTokens.Background.copy(alpha = 0.35f)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, CinemaTokens.Background))))
        Row(Modifier.fillMaxWidth().heightIn(min = if (wide) 390.dp else 350.dp).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
            if (wide) Artwork(if (info?.poster.isNullOrBlank()) item else item.copy(image = info!!.poster), Modifier.width(160.dp).aspectRatio(2f / 3))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiLabel(item.kind.label).uppercase(), color = CinemaTokens.Cyan, style = MaterialTheme.typography.labelLarge)
                Text(item.name, Modifier.semantics { heading() }, color = CinemaTokens.Text, style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                val facts = listOfNotNull(item.year ?: info?.year?.takeIf { it.toIntOrNull()?.let { year -> year > 1800 } == true }, info?.duration?.takeIf(String::isNotBlank), info?.genre?.takeIf(String::isNotBlank) ?: item.genre)
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = CinemaTokens.Secondary)
                val rating = info?.rating?.takeIf { it.toDoubleOrNull()?.let { number -> number > 0 } == true } ?: item.rating
                if (rating != null) Text(stringResource(R.string.provider_rating_value, rating), color = CinemaTokens.AccentText)
                info?.classification?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.content_rating_value, it), color = CinemaTokens.Secondary) }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun EpisodeCard(episode: Episode, record: WatchRecord?, watched: Boolean, play: () -> Unit, restart: () -> Unit, mark: () -> Unit) {
    ElevatedCard(onClick = play, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("episode_${episode.item.id}").focusRing(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.3f
                val title: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.episode_heading, episode.season, episode.number, android.text.BidiFormatter.getInstance().unicodeWrap(episode.item.name)), style = MaterialTheme.typography.titleMedium)
                        episode.durationSeconds?.let { Text(stringResource(R.string.duration_minutes_seconds, it / 60, it % 60), color = Muted) }
                        if (watched) Text(stringResource(R.string.ui_watched), color = AccentInk)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Artwork(episode.item, Modifier.fillMaxWidth().aspectRatio(16f / 9))
                    title()
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(episode.item, Modifier.width(180.dp).aspectRatio(16f / 9))
                    Box(Modifier.weight(1f)) { title() }
                }
            }
            if (episode.description.isNotBlank()) Text(episode.description, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Muted)
            if (record != null && record.duration > 0) LinearProgressIndicator(progress = { (record.position.toFloat() / record.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(if (record != null && record.position > 0 && !record.completed) stringResource(R.string.ui_resume_episode) else stringResource(R.string.ui_play_episode), Modifier.testTag("episode_play_${episode.item.id}"), primary = true, onClick = play)
                Action(stringResource(R.string.ui_from_beginning), onClick = restart)
                Action(if (watched) stringResource(R.string.ui_mark_unwatched) else stringResource(R.string.ui_mark_watched), Modifier.testTag("episode_mark_${episode.item.id}"), onClick = mark)
            }
        }
    }
}
@Composable private fun DetailFact(label: String, value: String) { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, style = MaterialTheme.typography.titleMedium); Text(value, color = Muted) } }
