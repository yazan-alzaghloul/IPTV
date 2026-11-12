package com.iptv.app

import android.content.Context

class PlaybackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("vod_playback", Context.MODE_PRIVATE)
    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", false)
        set(value) { prefs.edit().putBoolean("autoplay", value).apply() }
    var completionPercent: Int
        get() = prefs.getInt("completion", 95).coerceIn(80, 100)
        set(value) { prefs.edit().putInt("completion", value.coerceIn(80, 100)).apply() }
}
