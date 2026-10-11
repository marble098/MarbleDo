package com.marbledo.feature.calendar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Planning helpers that do not touch the ICU calendar, so they run on the JVM: turning an occasion
 * into a task or countdown request, listing favourites, and reporting catalog coverage.
 */
class OccasionPlannerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun occasion(
        month: Int,
        day: Int,
        titleFa: String = "مناسبت",
        titleEn: String = "Occasion $month-$day",
        category: OccasionCategory = OccasionCategory.NATIONAL,
    ) = Occasion(
        id = "jalali-$month-$day-$titleEn",
        titleFa = titleFa,
        titleEn = titleEn,
        category = category,
        calendar = OccasionCalendar.JALALI,
        month = month,
        day = day,
        isHoliday = false,
    )

    private val epochOfDay: Long = LocalDate.of(2026, 5, 10).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `plan request keeps the occasion day when no lead time is asked`() {
        val request = OccasionPlanner.planRequest(
            occasion = occasion(2, 20, titleFa = "روز گل محمدی"),
            epochMillis = epochOfDay,
            languageTag = "fa",
            zone = zone,
        )
        assertEquals("روز گل محمدی", request.title)
        assertEquals("2026-05-10", request.dateIso)
        assertFalse(request.asCountdown)
    }

    @Test
    fun `preparation request moves the task earlier and never into the future`() {
        val earlier = OccasionPlanner.planRequest(occasion(2, 20), epochOfDay, "en", daysBefore = 3, zone = zone)
        assertEquals("2026-05-07", earlier.dateIso)
        val clamped = OccasionPlanner.planRequest(occasion(2, 20), epochOfDay, "en", daysBefore = -4, zone = zone)
        assertEquals("2026-05-10", clamped.dateIso)
    }

    @Test
    fun `countdown requests keep the occasion title and day`() {
        val request = OccasionPlanner.planRequest(occasion(12, 30), epochOfDay, "en", asCountdown = true, zone = zone)
        assertTrue(request.asCountdown)
        assertEquals("Occasion 12-30", request.title)
        assertEquals("2026-05-10", request.dateIso)
    }

    @Test
    fun `favorites keep catalog order and ignore unknown ids`() {
        val first = occasion(1, 1, titleEn = "First")
        val second = occasion(2, 2, titleEn = "Second")
        val catalog = OccasionCatalog(listOf(first, second))
        val favorites = OccasionPlanner.favorites(catalog, setOf(second.id, "jalali-9-9-missing"))
        assertEquals(listOf(second), favorites)
    }

    @Test
    fun `coverage reports totals, months and categories`() {
        val catalog = OccasionCatalog(
            listOf(
                occasion(1, 1, category = OccasionCategory.NATIONAL),
                occasion(1, 5, category = OccasionCategory.RELIGIOUS),
                occasion(12, 29, category = OccasionCategory.NATIONAL),
                Occasion(
                    id = "gregorian-3-8-x",
                    titleFa = "روز جهانی",
                    titleEn = "Some World Day",
                    category = OccasionCategory.INTERNATIONAL,
                    calendar = OccasionCalendar.GREGORIAN,
                    month = 3,
                    day = 8,
                    isHoliday = false,
                ),
            ),
        )
        val coverage = OccasionPlanner.coverage(catalog)
        assertEquals(4, coverage.total)
        assertEquals(3, coverage.categoryCount)
        assertEquals(2, coverage.perCalendar[OccasionCalendar.JALALI])
        assertEquals(1, coverage.perCalendar[OccasionCalendar.GREGORIAN])
        assertEquals(2, coverage.coveredMonths)
        assertEquals(2, coverage.monthsPerCalendar[OccasionCalendar.JALALI]?.size)
        assertFalse(coverage.isFullYear)
        assertEquals(33, coverage.missingMonths.size)
        assertEquals(10, coverage.missingMonths.count { it.first == OccasionCalendar.JALALI })
        assertEquals(12, coverage.missingMonths.count { it.first == OccasionCalendar.LUNAR })
    }

    @Test
    fun `an empty catalog is reported as incomplete but not as a full year`() {
        val coverage = OccasionPlanner.coverage(OccasionCatalog.EMPTY)
        assertEquals(0, coverage.total)
        assertEquals(0, coverage.coveredMonths)
        assertFalse(coverage.isFullYear)
        assertEquals(36, coverage.missingMonths.size)
    }
}
