package com.iptv.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun read(): Credentials?
    fun save(credentials: Credentials)
    fun clear()
}
class SecureCredentialStore(context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("account_vault", Context.MODE_PRIVATE)
    private val alias = "iptv.account.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): Credentials? {
        val encoded = prefs.getString("sealed", null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return Gson().fromJson(cipher.doFinal(packed.copyOfRange(12, packed.size)).toString(Charsets.UTF_8), Credentials::class.java)
    }
    override fun save(credentials: Credentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(Gson().toJson(credentials).toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("sealed", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) { "Account could not be saved." }
    }
    override fun clear() {
        check(prefs.edit().clear().commit()) { "Saved account could not be cleared." }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
internal fun accountScope(c: Credentials): String = MessageDigest.getInstance("SHA-256").digest(
    Gson().toJson(c).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

interface KeyValueStore {
    fun completionPercent(): Int = 95
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}
class PreferenceValues(context: Context) : KeyValueStore {
    private val contextRef = context.applicationContext
    private val prefs = context.getSharedPreferences("local_library", Context.MODE_PRIVATE)
    override fun completionPercent() = PlaybackPreferences(contextRef).completionPercent
    override fun get(key: String) = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun clear() { check(prefs.edit().clear().commit()) }
}
data class WatchRecord(val item: CatalogItem, val position: Long, val duration: Long, val updatedAt: Long, val completed: Boolean = false)

interface LibraryRepository {
    fun watched(c: Credentials): Set<String>
    fun setWatched(c: Credentials, items: List<CatalogItem>, watched: Boolean)
    fun removeHistory(c: Credentials, item: CatalogItem)
    fun season(c: Credentials, seriesId: String): Int?
    fun saveSeason(c: Credentials, seriesId: String, season: Int)
    fun searches(c: Credentials): List<String>
    fun saveSearch(c: Credentials, query: String)
    fun history(c: Credentials): List<WatchRecord>
    fun clearHistory(c: Credentials)
    fun favorites(c: Credentials): List<CatalogItem>
    fun toggle(c: Credentials, item: CatalogItem)
    fun position(c: Credentials, item: CatalogItem): Long
    fun savePosition(c: Credentials, item: CatalogItem, position: Long, duration: Long)
    fun clear()
}
class LocalLibrary(private val store: KeyValueStore) : LibraryRepository {
    private val gson = Gson()
    private fun scope(c: Credentials) = accountScope(c)
    override fun favorites(c: Credentials): List<CatalogItem> = runCatching {
        gson.fromJson(store.get("${scope(c)}.favorites") ?: "[]", Array<CatalogItem>::class.java).toList()
    }.getOrDefault(emptyList())
    override fun toggle(c: Credentials, item: CatalogItem) {
        val current = favorites(c)
        val updated = if (current.any { it.key == item.key }) current.filterNot { it.key == item.key } else current + item
        store.put("${scope(c)}.favorites", gson.toJson(updated))
    }
    override fun position(c: Credentials, item: CatalogItem): Long = if (item.kind == ContentKind.LIVE) 0L
        else store.get("${scope(c)}.position.${item.key}")?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
    override fun history(c: Credentials): List<WatchRecord> = runCatching {
        gson.fromJson(store.get("${scope(c)}.history.v1") ?: "[]", Array<WatchRecord>::class.java).toList()
    }.getOrDefault(emptyList())
    override fun watched(c: Credentials): Set<String> = runCatching {
        gson.fromJson(store.get("${scope(c)}.watched.v1") ?: "[]", Array<String>::class.java).toSet()
    }.getOrDefault(emptySet()) + history(c).filter { it.completed }.map { it.item.key }
    override fun setWatched(c: Credentials, items: List<CatalogItem>, watched: Boolean) {
        val keys = items.map { it.key }.toSet()
        val set = if (watched) this.watched(c) + keys else this.watched(c) - keys
        store.put("${scope(c)}.watched.v1", gson.toJson(set))
        val history = history(c).map { if (it.item.key in keys) it.copy(completed = watched, position = 0) else it }
        store.put("${scope(c)}.history.v1", gson.toJson(history))
        keys.forEach { store.put("${scope(c)}.position.$it", "0") }
    }
    override fun removeHistory(c: Credentials, item: CatalogItem) {
        store.put("${scope(c)}.history.v1", gson.toJson(history(c).filterNot { it.item.key == item.key }))
        store.put("${scope(c)}.position.${item.key}", "0")
    }
    override fun season(c: Credentials, seriesId: String): Int? = store.get("${scope(c)}.season.$seriesId")?.toIntOrNull()
    override fun saveSeason(c: Credentials, seriesId: String, season: Int) { store.put("${scope(c)}.season.$seriesId", season.toString()) }
    override fun searches(c: Credentials): List<String> = runCatching {
        gson.fromJson(store.get("${scope(c)}.searches.v1") ?: "[]", Array<String>::class.java).toList()
    }.getOrDefault(emptyList())
    override fun saveSearch(c: Credentials, query: String) {
        val q = query.trim().take(120)
        if (q.isNotBlank()) store.put("${scope(c)}.searches.v1", gson.toJson((listOf(q) + searches(c).filterNot { it.equals(q, true) }).take(10)))
    }
    override fun clearHistory(c: Credentials) {
        history(c).forEach { store.put("${scope(c)}.position.${it.item.key}", "0") }
        store.put("${scope(c)}.history.v1", "[]")
    }
    override fun savePosition(c: Credentials, item: CatalogItem, position: Long, duration: Long) {
        val completed = item.kind != ContentKind.LIVE && duration > 0 && position.toDouble() / duration >= store.completionPercent().coerceIn(80, 100) / 100.0
        val record = WatchRecord(item, if (item.kind == ContentKind.LIVE) 0 else position.coerceAtLeast(0),
            duration.coerceAtLeast(0), System.currentTimeMillis(), completed)
        if (completed) store.put("${scope(c)}.watched.v1", gson.toJson(watched(c) + item.key))
        val recent = (listOf(record) + history(c).filterNot { it.item.key == item.key }).take(100)
        store.put("${scope(c)}.history.v1", gson.toJson(recent))
        if (item.kind == ContentKind.LIVE) return
        val value = if (completed) 0L else position.coerceAtLeast(0)
        store.put("${scope(c)}.position.${item.key}", value.toString())
    }
    override fun clear() = store.clear()
}
