package com.iptv.app

import com.google.gson.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ContentKind(val label: String, val api: String, val route: String) {
    LIVE("Live TV", "live", "live"), MOVIE("Movies", "vod", "movie"), SERIES("Series", "series", "series")
}
data class Credentials(val baseUrl: String, val username: String, val password: String) {
    override fun toString() = "Credentials([redacted])"
}
data class Category(val id: String, val name: String)
data class CatalogItem(val id: String, val name: String, val kind: ContentKind, val categoryId: String = "", val image: String = "", val extension: String = "mp4",
    val added: Long = 0, val rating: String? = null, val year: String? = null, val backdrop: String? = null, val genre: String? = null,
    val language: String? = null, val releaseDate: String? = null, val overview: String? = null,
    val parentSeriesId: String? = null, val parentSeriesName: String? = null,
    val seasonNumber: Int? = null, val episodeNumber: Int? = null) {
    val key get() = "${kind.name}:$id"
}
data class Episode(val item: CatalogItem, val season: Int, val number: Int, val description: String = "", val durationSeconds: Long? = null)
data class ContentDetails(val description: String = "", val metadata: String = "", val episodes: List<Episode> = emptyList(), val extension: String = "mp4", val cast: String = "", val director: String = "", val rating: String = "", val backdrop: String = "", val genre: String = "", val year: String = "", val duration: String = "",
    val language: String = "", val subtitles: String = "", val classification: String = "", val trailer: String? = null, val technical: String = "", val poster: String = "")
data class Programme(val title: String, val start: Long, val end: Long, val description: String = "")
class ProviderException(message: String) : IOException(message)

object LoginInput {
    fun normalize(host: String, port: String, scheme: String, username: String, password: String): Credentials {
        require(username.isNotBlank() && password.isNotBlank()) { "Enter your username and password." }
        require(scheme == "http" || scheme == "https") { "Choose HTTP or HTTPS." }
        val raw = host.trim()
        require(raw.isNotBlank()) { "Enter the provider host or URL." }
        val url = (if (raw.contains("://")) raw else "$scheme://$raw").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS provider URL.")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "Use the provider base URL without login details, a query, or a fragment."
        }
        require(!url.encodedPath.endsWith(".php") && !url.encodedPath.endsWith(".m3u")) {
            "Enter the base URL, not a playlist or player_api.php URL."
        }
        val builder = url.newBuilder()
        if (port.isNotBlank()) {
            val number = port.trim().toIntOrNull()
            require(number != null && number in 1..65535) { "Port must be a number between 1 and 65535." }
            builder.port(number)
        }
        if (!url.encodedPath.endsWith("/")) builder.addPathSegment("")
        return Credentials(builder.build().toString(), username.trim(), password)
    }
}

interface ProviderRepository {
    suspend fun validate(credentials: Credentials)
    suspend fun categories(credentials: Credentials, kind: ContentKind): List<Category>
    suspend fun catalog(credentials: Credentials, kind: ContentKind): List<CatalogItem>
    suspend fun details(credentials: Credentials, item: CatalogItem): ContentDetails
    suspend fun guide(credentials: Credentials, id: String): List<Programme>
    fun streamUrl(credentials: Credentials, item: CatalogItem): String
}

internal fun JsonObject.text(key: String, fallback: String = ""): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeUnless { it == "null" } ?: fallback
internal fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
internal fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
internal fun JsonArray.objects(): List<JsonObject> = filter { it.isJsonObject }.map { it.asJsonObject }

class XtreamRepository(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()) : ProviderRepository {
    private suspend fun request(c: Credentials, action: String? = null, parameters: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
        val url = c.baseUrl.toHttpUrlOrNull()!!.newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", c.username).addQueryParameter("password", c.password)
        action?.let { url.addQueryParameter("action", it) }
        parameters.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val call = client.newCall(Request.Builder().url(url.build()).build())
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ProviderException("Cannot reach the provider. Check the address, connection, and HTTP/HTTPS setting."))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) throw ProviderException(when (it.code) {
                                401, 403 -> "Access denied. Check your login and subscription."
                                404 -> "Provider API not found. Check the host, port, and base path."
                                else -> "Provider returned HTTP ${it.code}. Try again later."
                            })
                            val value = it.body?.string() ?: throw ProviderException("The provider returned an empty response.")
                            if (continuation.isActive) continuation.resume(value)
                        } catch (e: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(if (e is ProviderException) e else ProviderException("The provider connection was interrupted. Try again."))
                        }
                    }
                }
            })
        }
        try { JsonParser.parseString(body) } catch (_: JsonParseException) {
            throw ProviderException("The provider returned an unreadable response. Check the base URL and try again.")
        }
    }
    override suspend fun validate(credentials: Credentials) {
        val response = request(credentials)
        if (!response.isJsonObject) throw ProviderException("This address did not return a valid Xtream account.")
        val user = response.asJsonObject.obj("user_info")
        if (user.text("auth") != "1") throw ProviderException("Login failed. Check your username and password.")
        val status = user.text("status")
        if (!status.equals("Active", true)) throw ProviderException("Subscription is ${status.ifBlank { "unavailable" }}. Contact your provider.")
        val expires = user.text("exp_date").toLongOrNull()
        if (expires != null && expires > 0 && expires <= System.currentTimeMillis() / 1000)
            throw ProviderException("Your subscription has expired. Contact your provider.")
    }
    private fun list(response: JsonElement): List<JsonObject> {
        if (response.isJsonArray) return response.asJsonArray.objects()
        throw ProviderException("The provider could not load this catalog. Your session may have expired; retry or sign in again.")
    }
    override suspend fun categories(credentials: Credentials, kind: ContentKind) = list(request(credentials, "get_${kind.api}_categories"))
        .map { Category(it.text("category_id"), it.text("category_name", "Other")) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
    override suspend fun catalog(credentials: Credentials, kind: ContentKind): List<CatalogItem> = withContext(Dispatchers.Default) {
        val action = if (kind == ContentKind.SERIES) "get_series" else "get_${kind.api}_streams"
        list(request(credentials, action)).mapNotNull {
            val id = it.text(if (kind == ContentKind.SERIES) "series_id" else "stream_id")
            if (id.isBlank()) null else CatalogItem(id, it.text("name", "Untitled"), kind, it.text("category_id"),
                it.text("stream_icon", it.text("cover")), it.text("container_extension", "mp4"),
                added = it.text("added", it.text("last_modified")).toLongOrNull() ?: 0,
                rating = it.text("rating").takeIf { value -> value.toDoubleOrNull()?.let { score -> score > 0 } == true },
                year = it.text("year", it.text("releaseDate").take(4)).takeIf { value -> value.toIntOrNull() != null },
                genre = it.text("genre").takeIf { value -> value.isNotBlank() },
                backdrop = it.array("backdrop_path").firstOrNull()?.takeIf { value -> value.isJsonPrimitive }?.asString,
                language = it.text("language").takeIf { value -> value.isNotBlank() },
                releaseDate = it.text("releaseDate", it.text("releasedate")).takeIf { value -> value.isNotBlank() },
                overview = it.text("plot", it.text("description")).takeIf { value -> value.isNotBlank() })
        }.distinctBy { it.key }
    }
    override suspend fun details(credentials: Credentials, item: CatalogItem): ContentDetails = withContext(Dispatchers.Default) {
        val response = request(credentials, if (item.kind == ContentKind.SERIES) "get_series_info" else "get_vod_info",
            mapOf((if (item.kind == ContentKind.SERIES) "series_id" else "vod_id") to item.id))
        if (!response.isJsonObject) throw ProviderException("Details are unavailable. Try again later.")
        val root = response.asJsonObject
        if (root.obj("user_info").text("auth") == "0") throw ProviderException("Access denied. Check your login and subscription.")
        val info = root.obj("info")
        val episodes = root.obj("episodes").entrySet().flatMap { (season, value) ->
            if (!value.isJsonArray) emptyList() else value.asJsonArray.objects().mapNotNull { ep ->
                val id = ep.text("id")
                if (id.isBlank()) null else Episode(CatalogItem(id, ep.text("title", "Episode ${ep.text("episode_num")}"), ContentKind.SERIES,
                    categoryId = item.categoryId, image = ep.obj("info").text("movie_image", item.image), extension = ep.text("container_extension", "mp4"),
                    genre = item.genre, parentSeriesId = item.id, parentSeriesName = item.name,
                    seasonNumber = ep.text("season", season).toIntOrNull() ?: 0,
                    episodeNumber = ep.text("episode_num").toIntOrNull() ?: 0),
                    ep.text("season", season).toIntOrNull() ?: 0, ep.text("episode_num").toIntOrNull() ?: 0,
                    description = ep.obj("info").text("plot", ep.obj("info").text("description")),
                    durationSeconds = ep.obj("info").text("duration_secs").toLongOrNull()?.takeIf { duration -> duration > 0 }
                        ?: VodMetadata.durationSeconds(ep.obj("info").text("duration")))
            }
        }.sortedWith(compareBy({ it.season }, { it.number }))
        ContentDetails(info.text("plot", info.text("description")),
            listOf(info.text("genre"), info.text("releasedate", info.text("releaseDate")), info.text("duration")).filter { it.isNotBlank() }.joinToString(" • "),
            episodes, root.obj("movie_data").text("container_extension", item.extension),
            cast = info.text("cast", info.text("actors")), director = info.text("director"), rating = info.text("rating"),
            backdrop = info.array("backdrop_path").firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            genre = info.text("genre"), year = info.text("year", info.text("releasedate", info.text("releaseDate")).take(4)),
            duration = info.text("duration"), language = info.text("language"), subtitles = info.text("subtitles"), classification = info.text("mpaa", info.text("age")),
            trailer = VodMetadata.trailerUrl(info.text("youtube_trailer", info.text("trailer"))),
            poster = info.text("movie_image", info.text("cover")),
            technical = listOf(info.obj("video").text("codec_name"),
                info.obj("video").text("width").takeIf { it.toIntOrNull()?.let { n -> n > 0 } == true }?.let { width -> "$width × ${info.obj("video").text("height")}" })
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "))
    }
    override suspend fun guide(credentials: Credentials, id: String): List<Programme> {
        val response = request(credentials, "get_short_epg", mapOf("stream_id" to id, "limit" to "8"))
        if (!response.isJsonObject) return emptyList()
        return response.asJsonObject.array("epg_listings").objects().mapNotNull {
            val start = it.text("start_timestamp").toLongOrNull()
            val end = it.text("stop_timestamp", it.text("end_timestamp")).toLongOrNull()
            if (start == null || end == null || end <= start || start < 0) null else Programme(decodeTitle(it.text("title")), start, end, decodeTitle(it.text("description")))
        }.sortedBy { it.start }
    }
    override fun streamUrl(credentials: Credentials, item: CatalogItem): String {
        val extension = if (item.kind == ContentKind.LIVE) "m3u8" else item.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "mp4"
        return credentials.baseUrl.toHttpUrlOrNull()!!.newBuilder().addPathSegment(item.kind.route)
            .addPathSegment(credentials.username).addPathSegment(credentials.password)
            .addPathSegment("${item.id}.$extension").build().toString()
    }
    private fun decodeTitle(value: String): String = try {
        // Some providers send plain titles; accept Base64 only when it round-trips and is valid UTF-8.
        val bytes = Base64.getDecoder().decode(value)
        val decoded = bytes.toString(Charsets.UTF_8)
        if (decoded.any { it == '\uFFFD' || (it.isISOControl() && !it.isWhitespace()) }) value else decoded
    } catch (_: IllegalArgumentException) { value }
}
