package com.iptv.app

/** Epoch seconds throughout. UI formatting uses the device's timezone; geometry stays timezone independent. */
data class GuideWindow(val start: Long, val end: Long) {
    val duration: Long get() = (end - start).coerceAtLeast(1)
    fun fraction(time: Long): Float = ((time - start).toDouble() / duration).toFloat().coerceIn(0f, 1f)
}
object GuideLayout {
    const val HALF_HOUR = 1800L
    fun window(programmes: List<Programme>, now: Long): GuideWindow {
        val valid = programmes.filter { it.end > it.start }
        val start = Math.floorDiv(minOf(now, valid.minOfOrNull { it.start } ?: now), HALF_HOUR) * HALF_HOUR
        // A bounded short schedule avoids enormous layouts for corrupt provider timestamps.
        val end = minOf(maxOf(start + 7200, valid.maxOfOrNull { it.end } ?: now), start + 86400)
        return GuideWindow(start, end)
    }
    fun progress(programme: Programme, now: Long): Float = if (programme.end <= programme.start) 0f
        else ((now - programme.start).toDouble() / (programme.end - programme.start)).toFloat().coerceIn(0f, 1f)
    fun current(programmes: List<Programme>, now: Long) = programmes.firstOrNull { it.start <= now && now < it.end }
}
