package com.iptv.app

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import coil.compose.SubcomposeAsyncImage
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private var launchPending = false
    override fun onResume() { super.onResume(); launchPending = false }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val state = vm.state
            val navigationState = rememberSaveableStateHolder()
            var navigationAccount by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(state.restoring, state.signedIn, state.accountId) {
                if (!state.restoring) {
                    val next = state.accountId.takeIf { state.signedIn }
                    if (navigationAccount != next) {
                        navigationAccount?.let { navigationState.removeState("shell:$it") }
                        navigationAccount = next
                    }
                }
            }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refreshLibrary() }
            IptvTheme(state.appearance) {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    when {
                        state.restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !state.signedIn -> LoginScreen(state.busy, state.error, vm::login)
                        state.selected != null -> {
                            BackHandler { vm.closeDetails() }
                            if (state.selected.kind != ContentKind.LIVE) VodDetailsScreen(vm, vm::closeDetails) { item, restart -> launchPlayer(vm, item, restart) }
                            else DetailsScreen(state, vm::closeDetails, vm::toggle, { vm.select(state.selected) }, restart = { launchPlayer(vm, it, true) }) { item ->
                                launchPlayer(vm, item)
                            }
                        }
                        else -> navigationState.SaveableStateProvider("shell:${state.accountId}") { PremiumShell(vm) { launchPlayer(vm, it) } }
                    }
                }
            }
        }
    }
    private fun launchPlayer(vm: AppViewModel, item: CatalogItem, restart: Boolean = false) {
        if (launchPending) return
        launchPending = true
        val live = vm.state.catalog.filter { it.kind == ContentKind.LIVE }
        val index = live.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
        val queue = if (item.kind == ContentKind.LIVE) live.drop((index - 24).coerceAtLeast(0)).take(50) else emptyList()
        lifecycleScope.launch {
            try {
            val episodeInfo = vm.state.details?.episodes?.takeIf { list -> list.any { it.item.key == item.key } }
                ?: vm.state.featuredDetails?.episodes?.takeIf { list -> list.any { it.item.key == item.key } }
            val token = if (item.parentSeriesId != null && !episodeInfo.isNullOrEmpty())
                withContext(Dispatchers.IO) { vm.vod.queue(vm.state.accountId, episodeInfo.map { it.item }) } else null
            startActivity(Intent(this@MainActivity, PlayerActivity::class.java)
                .putExtra("item", Gson().toJson(vm.playbackItem(item)))
                .putExtra("queue", Gson().toJson(queue)).putExtra("restart", restart).putExtra("episodeQueue", token))
            } catch (e: CancellationException) { launchPending = false; throw e }
            catch (_: Exception) { launchPending = false; android.widget.Toast.makeText(this@MainActivity, localizedUiLabel(this@MainActivity, "Could not prepare playback. Try again."), android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

}

@Composable fun Modifier.focusRing(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.hasFocus }.border(if (focused) 3.dp else 0.dp,
        if (focused) Sky else Color.Transparent, RoundedCornerShape(12.dp))
}
@Composable fun Action(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val m = modifier.heightIn(min = 48.dp).focusRing()
    if (primary) Button(onClick, m, enabled = enabled, shape = RoundedCornerShape(12.dp)) { Text(label) }
    else OutlinedButton(onClick, m, enabled = enabled, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentInk)) { Text(label) }
}

@Composable fun LoginScreen(busy: Boolean, error: String?, onLogin: (String, String, String, String, String) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var scheme by rememberSaveable { mutableStateOf("https") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val submit = { onLogin(host, port, scheme, username, password) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.ui_iptv), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = AccentInk)
            Text(stringResource(R.string.ui_your_channels_one_place), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ui_sign_in_with_your_iptv_provider_s_xtream_account), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(scheme == "https", { scheme = "https" }, { Text(stringResource(R.string.ui_https)) }, modifier = Modifier.focusRing(), enabled = !busy)
                FilterChip(scheme == "http", { scheme = "http" }, { Text(stringResource(R.string.ui_http)) }, modifier = Modifier.focusRing(), enabled = !busy)
            }
            if (scheme == "http" || host.startsWith("http://", true)) Text(stringResource(R.string.ui_http_sends_your_login_and_video_without_encryption_use_https_when_your_provider_supports_it), style = MaterialTheme.typography.bodySmall, color = Muted)
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.ui_host_or_provider_url)) }, placeholder = { Text(stringResource(R.string.ui_provider_example_or_https_provider_example_path)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), colors = cinemaFieldColors())
            OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.ui_port_optional)) }, supportingText = { Text(stringResource(R.string.ui_leave_blank_to_use_the_url_port_or_http_https_default)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), colors = cinemaFieldColors())
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.ui_username)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false), colors = cinemaFieldColors())
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.ui_password)) }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { CinemaTextButton({ showPassword = !showPassword }, Modifier.focusRing()) { Text(if (showPassword) stringResource(R.string.ui_hide) else stringResource(R.string.ui_show)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }), colors = cinemaFieldColors())
            error?.let { ErrorMessage(it) }
            Action(if (busy) stringResource(R.string.ui_connecting) else stringResource(R.string.ui_connect), Modifier.fillMaxWidth(), enabled = !busy, primary = true, onClick = submit)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.ui_your_login_stays_on_this_device_channels_and_titles_come_from_your_subscription), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun Artwork(item: CatalogItem, modifier: Modifier) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
            Text(if (item.kind == ContentKind.LIVE) "LIVE" else item.name.take(1).uppercase(), color = AccentInk, style = MaterialTheme.typography.headlineMedium)
        }
    }
    SubcomposeAsyncImage(model = item.image, contentDescription = null, modifier = modifier.clip(CinemaTokens.PosterShape),
        contentScale = if (item.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop,
        loading = { fallback() }, error = { fallback() })
}

@Composable fun DetailsScreen(s: AppState, back: () -> Unit, toggle: (CatalogItem) -> Unit, retry: () -> Unit, restart: (CatalogItem) -> Unit = {}, play: (CatalogItem) -> Unit) {
    val item = s.selected ?: return
    var season by rememberSaveable(item.key) { mutableIntStateOf(-1) }
    val episodes = s.details?.episodes.orEmpty()
    val seasons = episodes.map { it.season }.distinct().sorted()
    val activeSeason = season.takeIf { it in seasons } ?: seasons.firstOrNull()
    val progress = s.history.associateBy { it.item.key }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(item.key) { while (true) { now = System.currentTimeMillis() / 1000; kotlinx.coroutines.delay(30_000) } }
    val current = s.guide.firstOrNull { it.start <= now && it.end > now }
    val next = s.guide.firstOrNull { it.start > now }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Action(stringResource(R.string.back_to, sectionTitle(s.section)), onClick = back) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                Artwork(item, Modifier.widthIn(max = 160.dp).weight(0.32f).height(170.dp))
                Column(Modifier.weight(0.68f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.kind.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = AccentInk)
                    Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    s.details?.metadata?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.kind != ContentKind.SERIES) Action(if (item.kind == ContentKind.LIVE) "Watch live" else "Watch / resume", Modifier.fillMaxWidth(), primary = true) { play(item) }
                if (item.kind == ContentKind.MOVIE && (progress[item.key]?.position ?: 0) > 0 && progress[item.key]?.completed != true)
                    Action(stringResource(R.string.ui_restart_from_beginning), Modifier.fillMaxWidth()) { restart(item) }
                Action(if (s.favorites.any { it.key == item.key }) stringResource(R.string.ui_remove_from_favorites) else stringResource(R.string.ui_add_to_favorites), Modifier.fillMaxWidth()) { toggle(item) }
            }
        }
        if (s.detailBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        s.detailError?.let { message -> item { ErrorMessage(message); Action(stringResource(R.string.ui_retry_details), onClick = retry) } }
        if (item.kind == ContentKind.LIVE) item {
            Text(stringResource(R.string.ui_on_this_channel), style = MaterialTheme.typography.titleLarge)
            ProgrammeRow(stringResource(R.string.ui_now), current)
            ProgrammeRow(stringResource(R.string.ui_next), next)
        }
        s.details?.description?.takeIf { it.isNotBlank() }?.let { description -> item { Text(description, style = MaterialTheme.typography.bodyLarge) } }
        s.details?.let { details ->
            if (details.rating.toDoubleOrNull()?.let { it > 0 } == true) item { Text("Provider rating · ${details.rating}", color = AccentInk) }
            if (details.cast.isNotBlank()) item { Text("Cast\n${details.cast}", color = Muted) }
            if (details.director.isNotBlank()) item { Text("Director\n${details.director}", color = Muted) }
        }
        if (item.kind == ContentKind.SERIES && !s.detailBusy) {
            if (s.details?.episodes.isNullOrEmpty()) item { Text(stringResource(R.string.ui_no_episodes_available_from_this_provider), color = Muted) }
            if (seasons.isNotEmpty()) item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.size) { i -> FilterChip(activeSeason == seasons[i], { season = seasons[i] }, { Text("Season ${seasons[i]}") }, modifier = Modifier.focusRing()) }
                }
            }
            items(episodes.filter { it.season == activeSeason }, key = { it.item.key }) { ep ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Action("S${ep.season} · E${ep.number}   ${ep.item.name}", Modifier.fillMaxWidth()) { play(ep.item) }
                    progress[ep.item.key]?.let { record ->
                        Text(if (record.completed) "Watched" else stringResource(R.string.resume_minutes, record.position / 60_000), color = Muted)
                        if (record.duration > 0) LinearProgressIndicator(progress = { (record.position.toFloat() / record.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
@Composable fun ProgrammeRow(label: String, programme: Programme?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AccentInk, style = MaterialTheme.typography.labelLarge)
        Text(programme?.title ?: stringResource(R.string.ui_program_information_unavailable))
        programme?.let {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            Text("${format.format(Date(it.start * 1000))} – ${format.format(Date(it.end * 1000))}", color = Muted)
        }
    }
}
@Composable fun ErrorMessage(message: String) { Text(uiLabel(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
