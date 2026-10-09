package com.marbledo.domain

import com.marbledo.domain.util.QuietHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuietHoursTest {
    private val start = 22 * 60
    private val end = 7 * 60

    @Test
    fun `a window that wraps past midnight covers the night`() {
        assertTrue(QuietHours.contains(23 * 60 + 30, start, end))
        assertTrue(QuietHours.contains(0, start, end))
        assertTrue(QuietHours.contains(6 * 60 + 59, start, end))
        assertTrue(QuietHours.contains(start, start, end))
    }

    @Test
    fun `the end minute is exclusive and daytime is outside the window`() {
        assertFalse(QuietHours.contains(end, start, end))
        assertFalse(QuietHours.contains(12 * 60, start, end))
        assertFalse(QuietHours.contains(21 * 60 + 59, start, end))
    }

    @Test
    fun `a same-day window does not wrap`() {
        assertTrue(QuietHours.contains(13 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(14 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(1 * 60, 12 * 60, 14 * 60))
    }

    @Test
    fun `equal start and end disables the window`() {
        assertFalse(QuietHours.contains(0, 600, 600))
        assertFalse(QuietHours.contains(600, 600, 600))
    }

    @Test
    fun `minutes until the end counts across midnight`() {
        assertEquals(7 * 60 + 30, QuietHours.minutesUntilEnd(23 * 60 + 30, start, end))
        assertEquals(60, QuietHours.minutesUntilEnd(6 * 60, start, end))
        assertEquals(0, QuietHours.minutesUntilEnd(12 * 60, start, end))
    }
}
