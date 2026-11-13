package com.iptv.app

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.res.stringResource

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.google.gson.Gson
import kotlinx.coroutines.*

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable fun SettingsScreen(s: AppState, update: (Appearance) -> Unit, clearHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    val a = s.appearance
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(Gson().toJson(a)) }
            }.isSuccess }
            message = if (success) "Appearance exported. Provider credentials are excluded." else "Could not export settings. Try another location."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) { runCatching {
                val input = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                    val buffer = CharArray(32_769); val length = reader.read(buffer)
                    require(length in 1..32_768); String(buffer, 0, length)
                }
                Gson().fromJson(input, Appearance::class.java).also {
                    require(it.theme in listOf("dark", "light") && it.accent in listOf("violet", "cyan"))
                    require(it.fontScale.isFinite() && it.fontScale in .85f..1.4f)
                    require(it.landing in listOf("HOME", "LIVE", "MOVIES", "SERIES")); require(it.hiddenRows.size <= 6)
                }
            }.getOrNull() }
            if (imported != null) { update(imported); message = "Appearance imported." } else message = "This is not a supported appearance settings file."
        }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text(stringResource(R.string.ui_clear_watch_history_question)) },
        text = { Text(stringResource(R.string.ui_this_also_resets_saved_progress_for_titles_in_your_recent_history_favorites_stay_saved)) },
        confirmButton = { CinemaTextButton({ clearHistory(); clearConfirm = false; message = "Watch history cleared." }) { Text(stringResource(R.string.ui_clear_history)) } },
        dismissButton = { CinemaTextButton({ clearConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { SettingsHeading(stringResource(R.string.ui_appearance), "Make the library comfortable on your screen.") }
        item { ChoiceRow(stringResource(R.string.ui_theme), listOf("dark" to "Cinematic dark", "light" to "Light"), a.theme) { update(a.copy(theme = it)) } }
        item { ChoiceRow(stringResource(R.string.ui_accent), listOf("violet" to "Violet", "cyan" to "Ocean"), a.accent) { update(a.copy(accent = it)) } }
        item { ToggleSetting(stringResource(R.string.ui_high_contrast), "Increase contrast for secondary text.", a.highContrast) { update(a.copy(highContrast = it)) } }
        item {
            Text(stringResource(R.string.text_size, (a.fontScale * 100).toInt()), style = MaterialTheme.typography.titleMedium)
            Slider(a.fontScale, { update(a.copy(fontScale = it)) }, valueRange = .85f..1.4f, steps = 10, modifier = Modifier.focusRing())
            Text(stringResource(R.string.ui_your_device_s_system_text_size_is_also_respected), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item { SettingsHeading(stringResource(R.string.ui_home), "Show the sections you use most.") }
        item { ChoiceRow(stringResource(R.string.ui_open_app_to), listOf("HOME" to "Home", "LIVE" to "Live TV", "MOVIES" to "Movies", "SERIES" to "Series"), a.landing) { update(a.copy(landing = it)) } }
        item { ToggleSetting(stringResource(R.string.ui_optional_motion), "Allow artwork rotation. System reduced-motion preferences take priority.", a.motion) { update(a.copy(motion = it)) } }
        item { ToggleSetting(stringResource(R.string.ui_rotate_featured_artwork), "Changes artwork every 12 seconds while the hero is not focused. No video autoplay.", a.heroRotation) { update(a.copy(heroRotation = it)) } }
        items(listOf("Continue watching", "Recently watched channels", "Favorites", "Live TV", "Movies", "Series")) { row ->
            ToggleSetting(row, "Show on Home when content is available.", row !in a.hiddenRows) { visible -> update(a.copy(hiddenRows = if (visible) a.hiddenRows - row else a.hiddenRows + row)) }
        }
        item {
            val preferences = remember { PlaybackPreferences(context) }
            var autoplay by remember { mutableStateOf(preferences.autoplay) }
            var completion by remember { mutableIntStateOf(preferences.completionPercent) }
            SettingsHeading(stringResource(R.string.ui_movies_episodes), "Playback completion and autoplay are stored on this device.")
            ToggleSetting(stringResource(R.string.ui_autoplay_next_episode), "Play the next provider episode after completion.", autoplay) { autoplay = it; preferences.autoplay = it }
            ChoiceRow(stringResource(R.string.ui_mark_complete_at), listOf(90,95,98,100).map { it.toString() to "$it%" }, completion.toString()) {
                completion = it.toInt(); preferences.completionPercent = completion
            }
        }
        item { SettingsHeading(stringResource(R.string.ui_data_privacy), "Your account and viewing history stay on this device.") }
        item { Action(stringResource(R.string.ui_clear_watch_history), Modifier.fillMaxWidth()) { clearConfirm = true } }
        item { Action(stringResource(R.string.ui_clear_artwork_cache), Modifier.fillMaxWidth()) {
            scope.launch {
                withContext(Dispatchers.IO) { context.imageLoader.memoryCache?.clear(); context.imageLoader.diskCache?.clear() }
                message = "Artwork cache cleared."
            }
        } }
        item { Action(stringResource(R.string.ui_export_appearance), Modifier.fillMaxWidth()) { export.launch("Rayan-TV-appearance.json") } }
        item { Action(stringResource(R.string.ui_import_appearance), Modifier.fillMaxWidth()) { import.launch(arrayOf("application/json", "text/plain")) } }
        item { Action(stringResource(R.string.ui_reset_appearance), Modifier.fillMaxWidth()) { update(Appearance()); message = "Appearance reset." } }
        message?.let { text -> item { Text(uiLabel(text), color = AccentInk) } }
        item {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val connected = manager.activeNetwork?.let { manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } == true
            val memory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            SettingsHeading(stringResource(R.string.ui_about_diagnostics), stringResource(R.string.about_diagnostics, version.orEmpty(), android.os.Build.VERSION.RELEASE, stringResource(if (connected) R.string.network_validated else R.string.network_offline), memory))
        }
    }
}
@Composable private fun SettingsHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(uiLabel(title), style = MaterialTheme.typography.titleLarge)
        Text(uiLabel(description), color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun ToggleSetting(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(uiLabel(title), style = MaterialTheme.typography.titleMedium); Text(uiLabel(description), color = Muted, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, change, Modifier.focusRing())
    }
}
@Composable private fun ChoiceRow(title: String, values: List<Pair<String, String>>, current: String, select: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(uiLabel(title), style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values) { (value, label) -> FilterChip(current == value, { select(value) }, { Text(uiLabel(label)) }, modifier = Modifier.focusRing()) }
        }
    }
}
