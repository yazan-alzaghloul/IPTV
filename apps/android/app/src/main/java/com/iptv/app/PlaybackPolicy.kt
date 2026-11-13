package com.iptv.app

/** One budget per selected stream. Only an explicit retry or channel change resets it. */
class RetryBudget(private val limit: Int = 2) {
    var attempts: Int = 0
        private set
    fun nextDelayMs(): Long? {
        if (attempts >= limit) return null
        return 1_000L shl attempts++
    }
    fun reset() { attempts = 0 }
}
