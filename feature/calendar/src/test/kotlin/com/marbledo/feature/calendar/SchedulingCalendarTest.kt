package com.marbledo.feature.calendar

import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Pure grid and formatting rules only. Calendar conversions use android.icu, which is not available to
 * JVM unit tests, so they are exercised on the device instead.
 */
class SchedulingCalendarTest {

    @Test
    fun `leading blank cells follow the chosen first day of the week`() {
        assertEquals(0, leadingBlankCells(7, weekStartsSaturday = true))
        assertEquals(1, leadingBlankCells(1, weekStartsSaturday = true))
        assertEquals(6, leadingBlankCells(6, weekStartsSaturday = true))
        assertEquals(0, leadingBlankCells(1, weekStartsSaturday = false))
        assertEquals(6, leadingBlankCells(7, weekStartsSaturday = false))
    }

    @Test
    fun `invalid ICU weekday numbers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { leadingBlankCells(0, weekStartsSaturday = true) }
        assertThrows(IllegalArgumentException::class.java) { leadingBlankCells(8, weekStartsSaturday = false) }
    }

    @Test
    fun `weekday headers start on the configured day`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), weekdayColumnOrder(weekStartsSaturday = true))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0), weekdayColumnOrder(weekStartsSaturday = false))
    }

    @Test
    fun `clock text is zero padded and uses the 24 hour form`() {
        assertEquals("08:05", formatClock(LocalTime.of(8, 5)))
        assertEquals("23:59", formatClock(LocalTime.of(23, 59)))
        assertEquals("00:00", formatClock(LocalTime.MIDNIGHT))
    }
}
