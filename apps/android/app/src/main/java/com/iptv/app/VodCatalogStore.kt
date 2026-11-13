package com.iptv.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import java.util.UUID
import java.util.Locale

/** Private additive cache. Credentials never enter the database. Call from an IO dispatcher. */
class VodCatalogStore private constructor(context: Context) : SQLiteOpenHelper(context, "vod_catalog.db", null, 1) {
    private val gson = Gson()
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE catalog(scope TEXT NOT NULL,kind TEXT NOT NULL,id TEXT NOT NULL,title TEXT NOT NULL,category TEXT NOT NULL,year TEXT NOT NULL,language TEXT NOT NULL,added INTEGER NOT NULL,rating REAL NOT NULL,released TEXT NOT NULL,genre TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(scope,kind,id))")
        db.execSQL("CREATE INDEX catalog_title ON catalog(scope,kind,title)")
        db.execSQL("CREATE INDEX catalog_category ON catalog(scope,kind,category)")
        db.execSQL("CREATE INDEX catalog_added ON catalog(scope,kind,added DESC)")
        db.execSQL("CREATE TABLE groups_cache(scope TEXT NOT NULL,kind TEXT NOT NULL,id TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(scope,kind,id))")
        db.execSQL("CREATE TABLE queues(token TEXT NOT NULL,scope TEXT NOT NULL,position INTEGER NOT NULL,json TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(token,position))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun replace(scope: String, kind: ContentKind, items: List<CatalogItem>, categories: List<Category> = emptyList()) {
        if (kind == ContentKind.LIVE) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("catalog", "scope=? AND kind=?", arrayOf(scope, kind.name))
            val statement = db.compileStatement("INSERT OR REPLACE INTO catalog VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")
            statement.use {
                items.forEach { item ->
                    it.clearBindings()
                    listOf(scope, kind.name, item.id, item.name.lowercase(Locale.ROOT), item.categoryId,
                        item.year.orEmpty(), item.language.orEmpty(), item.added.toString(), (item.rating?.toDoubleOrNull() ?: -1.0).toString(),
                        item.releaseDate ?: item.year.orEmpty(), item.genre.orEmpty(), gson.toJson(item)).forEachIndexed { index, value -> it.bindString(index + 1, value) }
                    it.executeInsert()
                }
            }
            db.delete("groups_cache", "scope=? AND kind=?", arrayOf(scope, kind.name))
            categories.forEach { category -> db.insertWithOnConflict("groups_cache", null, ContentValues().apply {
                put("scope", scope); put("kind", kind.name); put("id", category.id); put("name", category.name)
            }, SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun categories(scope: String, kind: ContentKind): List<Category> = readableDatabase.query("groups_cache", arrayOf("id", "name"), "scope=? AND kind=?", arrayOf(scope,kind.name), null,null,"name ASC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(Category(cursor.getString(0), cursor.getString(1))) }
    }
    fun page(scope: String, kind: ContentKind, filter: VodFilter = VodFilter(), offset: Int = 0, limit: Int = 48): VodPage {
        val args = mutableListOf(scope, kind.name)
        val where = StringBuilder("scope=? AND kind=?")
        filter.query.take(240).trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank).forEach { token ->
            where.append(" AND title LIKE ? ESCAPE '\\'")
            args += "%${token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        for ((field, value) in listOf("category" to filter.category, "year" to filter.year, "language" to filter.language)) {
            if (value.isNotBlank()) { where.append(" AND $field=?"); args += value }
        }
        if (filter.genre.isNotBlank()) { where.append(" AND genre LIKE ?"); args += "%${filter.genre.replace("%", "").replace("_", "")}%" }
        if (filter.ratedOnly) where.append(" AND rating>0")
        val order = when (filter.sort) {
            VodSort.TITLE -> "title ASC,id ASC"
            VodSort.ADDED -> "added DESC,title ASC,id ASC"
            VodSort.RELEASE -> "released DESC,title ASC,id ASC"
            VodSort.RATING -> "rating DESC,title ASC,id ASC"
        }
        val db = readableDatabase
        val total = db.rawQuery("SELECT count(*) FROM catalog WHERE $where", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
        val start = offset.coerceAtLeast(0)
        val items = db.query("catalog", arrayOf("json"), where.toString(), args.toTypedArray(), null, null, order,
            "${start},${limit.coerceIn(1, 100)}").use { cursor -> buildList {
                while (cursor.moveToNext()) add(gson.fromJson(cursor.getString(0), CatalogItem::class.java))
            } }
        return VodPage(items, total, start)
    }
    fun facets(scope: String, kind: ContentKind): VodFacets {
        fun values(column: String) = readableDatabase.rawQuery("SELECT DISTINCT $column FROM catalog WHERE scope=? AND kind=? AND $column<>'' ORDER BY $column DESC LIMIT 100", arrayOf(scope, kind.name)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val rating = readableDatabase.rawQuery("SELECT 1 FROM catalog WHERE scope=? AND kind=? AND rating>0 LIMIT 1", arrayOf(scope, kind.name)).use { it.moveToFirst() }
        return VodFacets(values("year"), values("language"), rating, values("genre").flatMap { it.split(',', '/', '|').map(String::trim) }.filter(String::isNotBlank).distinct().sorted())
    }
    fun find(scope: String, kind: ContentKind, id: String): CatalogItem? = readableDatabase.query("catalog", arrayOf("json"), "scope=? AND kind=? AND id=?", arrayOf(scope, kind.name, id), null, null, null, "1").use {
        if (it.moveToFirst()) gson.fromJson(it.getString(0), CatalogItem::class.java) else null
    }
    fun queue(scope: String, items: List<CatalogItem>): String {
        val token = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("queues", "token NOT IN (SELECT token FROM queues GROUP BY token ORDER BY max(created) DESC LIMIT 3)", null)
            items.forEachIndexed { index, item -> db.insertOrThrow("queues", null, ContentValues().apply {
                put("token", token); put("scope", scope); put("position", index); put("json", gson.toJson(item)); put("created", System.currentTimeMillis())
            }) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return token
    }
    fun loadQueue(scope: String, token: String): List<CatalogItem> = readableDatabase.query("queues", arrayOf("json"), "scope=? AND token=?", arrayOf(scope, token), null, null, "position ASC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(gson.fromJson(cursor.getString(0), CatalogItem::class.java)) }
    }
    fun clear(scope: String) {
        writableDatabase.delete("catalog", "scope=?", arrayOf(scope)); writableDatabase.delete("groups_cache", "scope=?", arrayOf(scope)); writableDatabase.delete("queues", "scope=?", arrayOf(scope))
    }
    companion object {
        @Volatile private var instance: VodCatalogStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: VodCatalogStore(context.applicationContext).also { instance = it } }
    }
}
