package com.marbledo.feature.calendar

import java.io.File
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression tests for the reported bug: the app showed the martyrdom of Fatima (3 Jumada al-Thani
 * 1448) on 23 Aban 1405, so 23 Aban looked like a day off. The statutory day off is 22 Aban 1405.
 *
 * The tests stay on the pure `java.time` side of the engine because Android's ICU calendars are
 * stubs in local unit tests. The expected Gregorian dates are the ones the official Iranian
 * calendar publishes for 1405 (`1405/08/22` = 2026-11-13, `1405/04/04` = 2026-06-25).
 */
class LunarAnchorsTest {
    private val assetText by lazy { assetFile().readText() }
    private val catalog by lazy { requireNotNull(OccasionCatalog.parse(assetText)) { "The bootstrap asset must parse" } }
    private val anchors by lazy { requireNotNull(catalog.lunarAnchors) { "Schema 6 requires the official table" } }

    private fun assetFile(): File = listOf(
        File("src/main/assets/calendar/occasions-v6-fa.json"),
        File("feature/calendar/src/main/assets/calendar/occasions-v6-fa.json"),
    ).first(File::exists)

    @Test
    fun `official table resolves 3 Jumada al-Thani 1448 to 22 Aban 1405`() {
        assertEquals(LocalDate.parse("2026-11-11"), anchors.monthStart(1448, 6), "1 Jumada al-Thani 1448 is 20 Aban 1405")
        assertEquals(30, anchors.monthLength(1448, 6))
        val resolved = OccasionDateRules.resolve(
            firstDay = anchors.monthStart(1448, 6)!!,
            monthLength = anchors.monthLength(1448, 6)!!,
            day = 3,
        )
        assertEquals(LocalDate.parse("2026-11-13"), resolved, "22 Aban 1405, not the arithmetic 2026-11-14 (23 Aban)")
    }

    @Test
    fun `ashura resolves to 4 Tir and not to the arithmetic 5 Tir`() {
        assertEquals(LocalDate.parse("2026-06-16"), anchors.monthStart(1448, 1), "1 Muharram 1448 is 26 Khordad 1405")
        val ashura = OccasionDateRules.resolve(
            firstDay = anchors.monthStart(1448, 1)!!,
            monthLength = anchors.monthLength(1448, 1)!!,
            day = 10,
        )
        assertEquals(LocalDate.parse("2026-06-25"), ashura, "4 Tir 1405; the arithmetic calendar would say 2026-06-26")
    }

    @Test
    fun `a 29 day Safar still puts the month end rule on the last real day`() {
        val start = anchors.monthStart(1448, 2)!!
        val length = anchors.monthLength(1448, 2)!!
        val last = OccasionDateRules.resolve(
            firstDay = start,
            monthLength = length,
            day = 30,
            rule = OccasionDateRule.MONTH_END,
        )
        assertEquals(start.plusDays(length - 1L), last)
        assertEquals(LocalDate.parse("2026-08-13"), last, "Last day of Safar 1448 is 22 Mordad 1405")
    }

    @Test
    fun `months are 29 or 30 days and never overlap`() {
        var previousEnd: LocalDate? = null
        for (year in 1446..1448) {
            for (month in 1..12) {
                val start = anchors.monthStart(year, month) ?: continue
                val length = anchors.monthLength(year, month) ?: continue
                assertTrue(length == 29 || length == 30, "Month $year/$month has $length days")
                if (previousEnd != null) assertEquals(previousEnd, start, "Gap before $year/$month")
                previousEnd = start.plusDays(length.toLong())
            }
        }
    }

    @Test
    fun `months outside the published table are marked as estimates`() {
        assertFalse(anchors.isEstimated(1448, 6))
        assertTrue(anchors.isEstimated(1449, 1))
        assertNotNull(anchors.monthStart(1449, 1))
        assertNull(anchors.monthStart(1200, 1), "A Hijri year before the table has no anchor")
        assertNull(anchors.monthStart(1448, 13), "Month 13 does not exist")
        assertEquals(1448 to 10, anchors.officialThrough())
    }

    @Test
    fun `uncovered months still fall back to the arithmetic calendar`() {
        val table = LunarAnchors.of(
            startYear = 1448,
            startMonth = 1,
            startDate = LocalDate.parse("2026-06-16"),
            officialLengths = intArrayOf(30, 29),
        )
        assertNotNull(table.monthStart(1448, 2))
        assertNull(table.monthStart(1448, 3), "No entry means the caller uses the ICU fallback")
    }

    @Test
    fun `the bootstrap carries the Fatima row as a public holiday`() {
        val fatima = catalog.occasions.filter {
            it.calendar == OccasionCalendar.LUNAR && it.month == 6 && it.day == 3
        }
        assertEquals(1, fatima.size)
        assertTrue(fatima.single().isHoliday, "3 Jumada al-Thani is a statutory day off")
        assertTrue(fatima.single().cultures.isNotEmpty())
    }

    @Test
    fun `a schema 6 document without anchors is rejected`() {
        val withoutAnchors = org.json.JSONObject(assetText).apply { remove("lunarAnchors") }
        assertNull(OccasionCatalog.parse(withoutAnchors.toString()))
    }

    @Test
    fun `an invalid month length is rejected`() {
        val broken = org.json.JSONObject(assetText).apply {
            getJSONObject("lunarAnchors").getJSONArray("officialLengths").put(0, 31)
        }
        assertNull(OccasionCatalog.parse(broken.toString()), "A 31-day lunar month is not a valid table")
    }
}
