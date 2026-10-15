package com.marbledo.feature.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WesternOccasionRulesTest {
    @Test fun `Western and Orthodox Easter match independent known-date vectors including century drift`() {
        val vectors = listOf(
            Triple(1818, "1818-03-22", "1818-04-26"), Triple(1900, "1900-04-15", "1900-04-22"),
            Triple(2024, "2024-03-31", "2024-05-05"), Triple(2025, "2025-04-20", "2025-04-20"),
            Triple(2026, "2026-04-05", "2026-04-12"), Triple(2027, "2027-03-28", "2027-05-02"),
            Triple(2038, "2038-04-25", "2038-04-25"), Triple(2100, "2100-03-28", "2100-05-02"),
            Triple(2101, "2101-04-17", "2101-04-24"), Triple(2200, "2200-04-06", "2200-04-06"),
        )
        vectors.forEach { (year, western, orthodox) ->
            assertEquals(LocalDate.parse(western), OccasionDateRules.westernEaster(year))
            assertEquals(LocalDate.parse(orthodox), OccasionDateRules.orthodoxEaster(year))
        }
    }

    @Test fun `Easter offsets calculate Lent Good Friday Ascension and Pentecost across March boundaries`() {
        val first = LocalDate.of(2026, 3, 1)
        listOf(-47 to "2026-02-17", -46 to "2026-02-18", -2 to "2026-04-03", 1 to "2026-04-06",
            39 to "2026-05-14", 49 to "2026-05-24", 60 to "2026-06-04").forEach { (offset, expected) ->
            assertEquals(LocalDate.parse(expected), OccasionDateRules.resolve(first, 31, 1, OccasionDateRule.WESTERN_EASTER, dayOffset = offset))
        }
    }

    @Test fun `Thanksgiving Black Friday and Cyber Monday are relative to the fourth Thursday`() {
        for (year in 2024..2105) {
            val first = LocalDate.of(year, 11, 1)
            val thanks = OccasionDateRules.resolve(first, 30, 1, OccasionDateRule.NTH_WEEKDAY, 4, 4)!!
            assertEquals(DayOfWeek.THURSDAY, thanks.dayOfWeek)
            assertTrue(thanks.dayOfMonth in 22..28)
            assertEquals(DayOfWeek.FRIDAY, OccasionDateRules.resolve(first, 30, 1, OccasionDateRule.NTH_WEEKDAY, 4, 4, 1)!!.dayOfWeek)
            assertEquals(thanks.plusDays(4), OccasionDateRules.resolve(first, 30, 1, OccasionDateRule.NTH_WEEKDAY, 4, 4, 4))
        }
    }

    @Test fun `Advent begins in the November 27 to December 3 window and never after Christmas`() {
        for (year in 2024..2105) {
            val first = OccasionDateRules.resolve(LocalDate.of(year, 12, 1), 31, 24,
                OccasionDateRule.WEEKDAY_ON_OR_BEFORE, weekday = 7, dayOffset = -21)!!
            assertEquals(DayOfWeek.SUNDAY, first.dayOfWeek)
            assertTrue(first >= LocalDate.of(year, 11, 27) && first <= LocalDate.of(year, 12, 3))
            assertTrue(first.plusDays(21) < LocalDate.of(year, 12, 25))
        }
    }

    @Test fun `Victoria Day is Monday before May 25 including when May 24 is itself a Monday`() {
        for (year in 2024..2105) {
            val date = OccasionDateRules.resolve(LocalDate.of(year, 5, 1), 31, 24,
                OccasionDateRule.WEEKDAY_ON_OR_BEFORE, weekday = 1)!!
            assertEquals(DayOfWeek.MONDAY, date.dayOfWeek)
            assertTrue(date.dayOfMonth in 18..24)
        }
    }

    @Test fun `Kings Day uses Saturday on Sunday years not a substitute Monday`() {
        assertEquals(LocalDate.of(2025, 4, 26), OccasionDateRules.resolve(LocalDate.of(2025, 4, 1), 30, 27, OccasionDateRule.SUNDAY_TO_SATURDAY))
        assertEquals(LocalDate.of(2026, 4, 27), OccasionDateRules.resolve(LocalDate.of(2026, 4, 1), 30, 27, OccasionDateRule.SUNDAY_TO_SATURDAY))
    }

    @Test fun `invalid anchors offsets and unsupported computus years fail safely`() {
        assertNull(OccasionDateRules.resolve(LocalDate.of(2026, 2, 1), 28, 30, OccasionDateRule.WEEKDAY_ON_OR_BEFORE, weekday = 1))
        assertNull(OccasionDateRules.resolve(LocalDate.of(2026, 2, 1), 28, 1, OccasionDateRule.WEEKDAY_ON_OR_BEFORE, weekday = 9))
        assertThrows(IllegalArgumentException::class.java) { OccasionDateRules.westernEaster(1500) }
        assertThrows(IllegalArgumentException::class.java) {
            OccasionDateRules.resolve(LocalDate.of(2026, 3, 1), 31, 1, OccasionDateRule.WESTERN_EASTER, dayOffset = 61)
        }
    }
}
