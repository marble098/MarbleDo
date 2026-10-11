package com.marbledo.domain.util

/** A local UI discoverability gesture, NOT authentication or an access-control boundary. */
class HiddenTapSequence(private val requiredTaps: Int = 7, private val windowMillis: Long = 8_000) {
    init { require(requiredTaps > 0 && windowMillis > 0) }
    private var firstTap: Long? = null
    private var lastTap: Long? = null
    private var count = 0

    fun tap(nowElapsedMillis: Long): Boolean {
        val start = firstTap
        if (start == null || nowElapsedMillis - start > windowMillis || nowElapsedMillis < (lastTap ?: start)) {
            firstTap = nowElapsedMillis
            count = 0
        }
        lastTap = nowElapsedMillis
        count++
        if (count < requiredTaps) return false
        firstTap = null
        lastTap = null
        count = 0
        return true
    }
}
