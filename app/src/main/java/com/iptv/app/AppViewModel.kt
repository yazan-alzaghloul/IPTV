package com.iptv.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Section(val label: String, val kind: ContentKind?) {
    HOME("Home", null), LIVE("Live TV", ContentKind.LIVE), MOVIES("Movies", ContentKind.MOVIE),
    SERIES("Series", ContentKind.SERIES), FAVORITES("Favorites", null), SEARCH("Search", null), SETTINGS("Settings", null)
}
data class AppState(
    val restoring: Boolean = true, val signedIn: Boolean = false, val busy: Boolean = false,
    val error: String? = null, val section: Section = Section.HOME,
    val categories: List<Category> = emptyList(), val catalog: List<CatalogItem> = emptyList(),
    val category: String = "", val search: String = "", val favorites: List<CatalogItem> = emptyList(),
    val selected: CatalogItem? = null, val details: ContentDetails? = null, val guide: List<Programme> = emptyList(),
    val detailBusy: Boolean = false, val detailError: String? = null,
    val history: List<WatchRecord> = emptyList(), val appearance: Appearance = Appearance(),
    val sort: CatalogSort = CatalogSort.PROVIDER,
    val accountId: String = "", val vodRevision: Int = 0, val watched: Set<String> = emptySet(),
    val selectedSeason: Int? = null, val recentSearches: List<String> = emptyList(),
    val featuredKey: String? = null, val featuredDetails: ContentDetails? = null,
    val livePreview: CatalogItem? = null, val liveGuide: List<Programme> = emptyList(),
    val liveGuideBusy: Boolean = false, val liveGuideError: String? = null
)
private data class CachedCatalog(val items: List<CatalogItem>, val categories: List<Category>, val loadedAt: Long)
class AppViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val provider: ProviderRepository = XtreamRepository()
    private val credentialsStore: CredentialStore = SecureCredentialStore(application)
    private val library: LibraryRepository = LocalLibrary(PreferenceValues(application))
    private val preferences = AppPreferences(application)
    val vod = VodCatalogStore.get(application)
    private val detailCache = linkedMapOf<String, ContentDetails>()
    private var featuredJob: Job? = null
    private var guideJob: Job? = null
    private val guideCache = linkedMapOf<String, Pair<Long, List<Programme>>>()
    private var credentials: Credentials? = null
    private val cache = mutableMapOf<ContentKind, CachedCatalog>()
    private val permits = Semaphore(2)
    var state by mutableStateOf(AppState(appearance = preferences.read())); private set
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { credentialsStore.read() }
                if (saved != null) {
                    credentials = saved
                    state = state.copy(restoring = false, signedIn = true, favorites = library.favorites(saved),
                        history = library.history(saved), section = landing(), accountId = accountScope(saved), watched = library.watched(saved), recentSearches = library.searches(saved))
                    loadCatalog()
                } else state = state.copy(restoring = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                withContext(Dispatchers.IO) { runCatching { credentialsStore.clear(); library.clear() } }
                state = AppState(restoring = false, appearance = preferences.read(), error = "Saved login could not be unlocked. Please sign in again.")
            }
        }
    }
    private fun landing() = runCatching { Section.valueOf(savedState.get<String>("section") ?: state.appearance.landing) }.getOrDefault(Section.HOME)
    fun login(host: String, port: String, scheme: String, username: String, password: String) {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val account = LoginInput.normalize(host, port, scheme, username, password)
                provider.validate(account)
                withContext(Dispatchers.IO) { credentialsStore.save(account) }
                credentials = account
                cache.clear()
                state = AppState(restoring = false, signedIn = true, favorites = library.favorites(account),
                    history = library.history(account), appearance = preferences.read(), section = landing(), accountId = accountScope(account), watched = library.watched(account), recentSearches = library.searches(account))
                loadCatalog()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(busy = false, error = friendly(e)) }
        }
    }
    fun selectSection(section: Section) {
        if (state.section == section) return
        detailJob?.cancel(); guideJob?.cancel()
        savedState["section"] = section.name
        state = state.copy(liveGuideBusy = false, section = section, selected = null,
            search = savedState.get<String>("query:${section.name}").orEmpty(),
            category = savedState.get<String>("category:${section.name}").orEmpty(),
            sort = runCatching { CatalogSort.valueOf(savedState.get<String>("sort:${section.name}") ?: "PROVIDER") }.getOrDefault(CatalogSort.PROVIDER), error = null)
        loadCatalog()
    }
    fun refresh() = loadCatalog(true)
    fun loadCatalog(force: Boolean = false) {
        catalogJob?.cancel()
        val c = credentials ?: return
        val section = state.section
        refreshLibrary()
        if (section == Section.SETTINGS) { state = state.copy(busy = false, error = null); return }
        if (section == Section.FAVORITES) {
            state = state.copy(catalog = state.favorites, categories = emptyList(), busy = false, error = null); return
        }
        val kinds = section.kind?.let { listOf(it) } ?: ContentKind.entries
        val available = kinds.mapNotNull { cache[it] }
        state = state.copy(catalog = available.flatMap { it.items }, categories = if (kinds.size == 1) available.firstOrNull()?.categories ?: emptyList() else emptyList(), error = null)
        val needed = kinds.filter { force || cache[it] == null || System.currentTimeMillis() - cache.getValue(it).loadedAt > 300_000 }
        if (needed.isEmpty()) { state = state.copy(busy = false); return }
        state = state.copy(busy = true)
        catalogJob = viewModelScope.launch {
            try {
                provider.validate(c)
                supervisorScope {
                    val results = needed.map { kind -> async {
                        permits.withPermit {
                            try {
                                coroutineScope {
                                    val categories = async {
                                        try { provider.categories(c, kind) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                                    }
                                    val items = provider.catalog(c, kind)
                                    val groups = categories.await()
                                    if (kind != ContentKind.LIVE) withContext(Dispatchers.IO) { vod.replace(accountScope(c), kind, items, groups) }
                                    kind to Result.success(CachedCatalog(items, groups, System.currentTimeMillis()))
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { kind to Result.failure<CachedCatalog>(e) }
                        }
                    } }.awaitAll()
                    results.forEach { (kind, result) -> result.getOrNull()?.let { cache[kind] = it } }
                    val error = results.firstNotNullOfOrNull { it.second.exceptionOrNull() as? Exception }
                    state = state.copy(catalog = kinds.flatMap { cache[it]?.items.orEmpty() },
                        categories = if (kinds.size == 1) cache[kinds.first()]?.categories.orEmpty() else emptyList(),
                        busy = false, error = error?.let(::friendly), vodRevision = state.vodRevision + 1)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(busy = false, error = friendly(e)) }
        }
    }
    fun search(value: String) { savedState["query:${state.section.name}"] = value; state = state.copy(search = value) }
    fun category(value: String) { savedState["category:${state.section.name}"] = value; state = state.copy(category = value) }
    fun sort(value: CatalogSort) { savedState["sort:${state.section.name}"] = value.name; state = state.copy(sort = value) }
    fun appearance(value: Appearance) { preferences.save(value); state = state.copy(appearance = value) }
    fun refreshLibrary() {
        val c = credentials ?: return
        state = state.copy(history = library.history(c), favorites = library.favorites(c), watched = library.watched(c), recentSearches = library.searches(c))
    }
    fun clearHistory() {
        val c = credentials ?: return
        library.clearHistory(c); refreshLibrary()
    }
    fun select(requested: CatalogItem) {
        val item = requested.parentSeriesId?.let { parent ->
            cache[ContentKind.SERIES]?.items?.firstOrNull { it.id == parent }
                ?: requested.copy(id = parent, name = requested.parentSeriesName ?: requested.name, parentSeriesId = null, parentSeriesName = null, seasonNumber = null, episodeNumber = null)
        } ?: requested
        detailJob?.cancel()
        val c = credentials ?: return
        state = state.copy(selected = item, details = null, guide = emptyList(), detailBusy = true, detailError = null, selectedSeason = library.season(c, item.id))
        detailJob = viewModelScope.launch {
            try {
                if (item.kind == ContentKind.LIVE) state = state.copy(guide = provider.guide(c, item.id), detailBusy = false)
                else {
                    val details = detailCache[item.key] ?: provider.details(c, item)
                    rememberDetails(item, details)
                    state = state.copy(details = details, detailBusy = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = state.copy(detailBusy = false, detailError = friendly(e)) }
        }
    }
    fun previewChannel(item: CatalogItem, force: Boolean = false) {
        if (item.kind != ContentKind.LIVE) return
        if (!force && state.livePreview?.key == item.key && (state.liveGuideBusy || guideCache[item.key]?.let { System.currentTimeMillis() - it.first < 120_000 } == true)) return
        guideJob?.cancel()
        val c = credentials ?: return
        val cached = guideCache[item.key]?.takeIf { !force && System.currentTimeMillis() - it.first < 120_000 }
        state = state.copy(livePreview = item, liveGuide = cached?.second.orEmpty(), liveGuideBusy = cached == null, liveGuideError = null)
        if (cached != null) return
        guideJob = viewModelScope.launch {
            try {
                delay(200) // Coalesce rapid remote focus movement; no catalog-wide EPG fan-out.
                val guide = provider.guide(c, item.id)
                guideCache[item.key] = System.currentTimeMillis() to guide
                if (guideCache.size > 24) guideCache.remove(guideCache.keys.first())
                if (state.livePreview?.key == item.key) state = state.copy(liveGuide = guide, liveGuideBusy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (state.livePreview?.key == item.key) state = state.copy(liveGuideBusy = false, liveGuideError = friendly(e)) }
        }
    }
    fun previewUrl(item: CatalogItem): String? = credentials?.let { provider.streamUrl(it, item) }
    private fun rememberDetails(item: CatalogItem, details: ContentDetails) {
        detailCache[item.key] = details
        if (detailCache.size > 12) detailCache.remove(detailCache.keys.first())
    }
    fun loadFeatured(item: CatalogItem) {
        if (state.featuredKey == item.key) return
        featuredJob?.cancel()
        state = state.copy(featuredKey = item.key, featuredDetails = detailCache[item.key])
        val c = credentials ?: return
        if (detailCache[item.key] != null) return
        featuredJob = viewModelScope.launch {
            try {
                val details = provider.details(c, item)
                rememberDetails(item, details)
                if (state.featuredKey == item.key) state = state.copy(featuredDetails = details)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Optional hero metadata; details has an explicit retry. */ }
        }
    }
    fun saveSeason(seriesId: String, season: Int) {
        credentials?.let { library.saveSeason(it, seriesId, season) }
        state = state.copy(selectedSeason = season)
    }
    fun markWatched(items: List<CatalogItem>, watched: Boolean) {
        credentials?.let { library.setWatched(it, items, watched) }; refreshLibrary()
    }
    fun removeHistory(item: CatalogItem) { credentials?.let { library.removeHistory(it, item) }; refreshLibrary() }
    fun rememberSearch(query: String) { credentials?.let { library.saveSearch(it, query) }; refreshLibrary() }
    fun closeDetails() { detailJob?.cancel(); state = state.copy(selected = null, details = null, guide = emptyList(), detailError = null) }
    fun toggle(item: CatalogItem) {
        val c = credentials ?: return
        library.toggle(c, item)
        val favorites = library.favorites(c)
        state = state.copy(favorites = favorites, catalog = if (state.section == Section.FAVORITES) favorites else state.catalog)
    }
    fun playbackItem(item: CatalogItem): CatalogItem =
        if (item.kind == ContentKind.MOVIE) item.copy(extension = state.details?.extension ?: item.extension) else item
    fun signOut() {
        catalogJob?.cancel(); detailJob?.cancel(); featuredJob?.cancel(); guideJob?.cancel(); guideCache.clear(); cache.clear(); detailCache.clear()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { credentials?.let { vod.clear(accountScope(it)) }; credentialsStore.clear(); library.clear() }
                credentials = null
                savedState.keys().toList().forEach { savedState.remove<Any>(it) }
                state = AppState(restoring = false, appearance = preferences.read())
            } catch (_: Exception) { state = state.copy(error = "Could not clear saved data. Try signing out again.") }
        }
    }
    private fun friendly(e: Exception): String = when (e) {
        is ProviderException, is IllegalArgumentException -> e.message ?: "Check your details and try again."
        else -> "Could not complete this request. Check your connection and try again."
    }
}
