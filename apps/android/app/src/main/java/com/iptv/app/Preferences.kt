package com.iptv.app

import android.content.Context
import androidx.compose.runtime.*

data class Appearance(
    val theme: String = "dark", val accent: String = "violet", val fontScale: Float = 1f,
    val highContrast: Boolean = false, val motion: Boolean = false, val heroRotation: Boolean = false,
    val landing: String = "HOME", val hiddenRows: Set<String> = emptySet()
)
class AppPreferences(context: Context) {
    private val values = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun read() = Appearance(
        theme = values.getString("theme", "dark") ?: "dark", accent = values.getString("accent", "violet") ?: "violet",
        fontScale = values.getFloat("font", 1f).coerceIn(0.85f, 1.4f), highContrast = values.getBoolean("contrast", false),
        motion = values.getBoolean("motion", false), heroRotation = values.getBoolean("rotate", false),
        landing = values.getString("landing", "HOME") ?: "HOME", hiddenRows = values.getStringSet("hiddenRows", emptySet())!!.toSet()
    )
    fun save(a: Appearance) {
        values.edit().putString("theme", a.theme).putString("accent", a.accent).putFloat("font", a.fontScale)
            .putBoolean("contrast", a.highContrast).putBoolean("motion", a.motion).putBoolean("rotate", a.heroRotation)
            .putString("landing", a.landing).putStringSet("hiddenRows", a.hiddenRows).apply()
    }
}
enum class CatalogSort(val label: String) { PROVIDER("Provider order"), NAME("Name A–Z"), RECENT("Recently added"), RATING("Provider rating") }
object CatalogQuery {
    fun apply(items: List<CatalogItem>, query: String, category: String = "", sort: CatalogSort = CatalogSort.PROVIDER): List<CatalogItem> {
        val words = query.trim().lowercase(java.util.Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        val filtered = items.filter { item ->
            (category.isEmpty() || item.categoryId == category) && words.all { word ->
                item.name.lowercase(java.util.Locale.ROOT).contains(word) || item.genre.orEmpty().lowercase(java.util.Locale.ROOT).contains(word)
            }
        }
        return when (sort) {
            CatalogSort.PROVIDER -> filtered
            CatalogSort.NAME -> filtered.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            CatalogSort.RECENT -> filtered.sortedByDescending { it.added }
            CatalogSort.RATING -> filtered.sortedByDescending { it.rating?.toDoubleOrNull() ?: -1.0 }
        }
    }
}
