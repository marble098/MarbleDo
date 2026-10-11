package com.marbledo.feature.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionDateRulesTest {
    @Test fun `month-end lunar anniversaries never disappear in 29-day months`() {
        val start = LocalDate.of(2026, 1, 1)
        for (length in 29..30) {
            assertEquals(start.plusDays(length - 1L), OccasionDateRules.resolve(start, length, 30, OccasionDateRule.MONTH_END))
        }
        assertNull(OccasionDateRules.resolve(start, 29, 30))
    }

    @Test fun `Quds remains the last Friday even after lunar offset and well beyond 1410`() {
        for (year in 2025..2050) for (offset in -2L..2L) for (length in 29..30) {
            val start = LocalDate.of(year, 2, 1).minusDays(offset)
            val date = OccasionDateRules.resolve(start, length, 1, OccasionDateRule.LAST_WEEKDAY, weekday = 5)!!
            assertEquals(DayOfWeek.FRIDAY, date.dayOfWeek)
            assertTrue(date >= start && date < start.plusDays(length.toLong()))
            assertTrue(date.plusDays(7) >= start.plusDays(length.toLong()))
        }
    }

    @Test fun `Chaharshanbe Suri is the evening before the last Wednesday not a guessed year day`() {
        for (length in 29..30) {
            val start = LocalDate.of(2035, 2, 20)
            val date = OccasionDateRules.resolve(start, length, 1, OccasionDateRule.LAST_WEEKDAY, weekday = 3, dayOffset = -1)!!
            assertEquals(DayOfWeek.TUESDAY, date.dayOfWeek)
            assertTrue(date.plusDays(1) < start.plusDays(length.toLong()))
            assertTrue(date.plusDays(8) >= start.plusDays(length.toLong()))
        }
    }

    @Test fun `second Friday rule and missing fifth weekday are handled strictly`() {
        val start = LocalDate.of(2026, 2, 1)
        assertEquals(LocalDate.of(2026, 2, 13), OccasionDateRules.resolve(start, 28, 1,
            OccasionDateRule.NTH_WEEKDAY, weekday = 5, ordinal = 2))
        assertNull(OccasionDateRules.resolve(start, 28, 1, OccasionDateRule.NTH_WEEKDAY, weekday = 5, ordinal = 5))
        assertNull(OccasionDateRules.resolve(start, 28, 1, OccasionDateRule.LAST_WEEKDAY, weekday = 8))
    }

    @Test fun `local-date arithmetic crosses DST without moving a day`() {
        val start = LocalDate.of(2026, 3, 1)
        assertEquals(LocalDate.of(2026, 3, 31), OccasionDateRules.resolve(start, 31, 31))
    }
}
