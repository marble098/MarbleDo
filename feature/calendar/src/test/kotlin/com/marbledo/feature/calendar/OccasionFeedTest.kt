package com.marbledo.feature.calendar

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the online layer: the published feed must stay parseable, the catalog must come from the
 * internet instead of the bundle, and the independent holiday lists must only ever be a cross-check.
 */
class OccasionFeedTest {
    private fun publishedFeedFile(): File = listOf(
        File("../../data/occasions/occasions-live.json"),
        File("data/occasions/occasions-live.json"),
        File("../data/occasions/occasions-live.json"),
    ).firstOrNull(File::exists) ?: error("Published feed not found from ${File(".").absolutePath}")

    @Test
    fun `published feed parses and keeps the official lunar anchors`() {
        val catalog = requireNotNull(OccasionCatalog.parse(publishedFeedFile().readText()))
        assertEquals(6, catalog.schemaVersion)
        assertTrue(catalog.occasions.size >= 1_000, "The online feed is the descriptive catalog, found ${catalog.occasions.size}")
        assertNotNull(catalog.lunarAnchors)
        assertTrue(catalog.schoolPrograms.size >= 20)
        assertTrue(catalog.sources.all { it.tier.isNotEmpty() })
        assertTrue(catalog.sources.any { it.tier == "official" })
        assertTrue(catalog.sources.any { it.tier == "unofficial" })
        assertTrue(catalog.sources.any { it.tier == "open-source" })
    }

    @Test
    fun `the bundled asset is only a cold start seed`() {
        val bundled = listOf(
            File("src/main/assets/calendar/occasions-v6-fa.json"),
            File("feature/calendar/src/main/assets/calendar/occasions-v6-fa.json"),
        ).first(File::exists)
        val catalog = requireNotNull(OccasionCatalog.parse(bundled.readText()))
        assertTrue(catalog.occasions.size < 200, "The bundle must not ship the occasion database")
        assertTrue(catalog.occasions.all { it.isHoliday }, "The bundle only carries statutory days off")
        assertNotNull(catalog.lunarAnchors)
    }

    @Test
    fun `mirrors all point at the published feed over https`() {
        val mirrors = OccasionFeeds.catalogMirrors
        assertTrue(mirrors.size >= 3)
        assertTrue(mirrors.all { it.url.startsWith("https://") && it.catalog })
        assertTrue(mirrors.all { it.url.contains("data/occasions/occasions-live.json") })
        assertEquals(mirrors.size, OccasionFeeds.urls.size)
    }

    @Test
    fun `direct community feeds are year aware and never served as the catalog`() {
        val feeds = OccasionFeeds.holidayDayFeeds(1405)
        assertTrue(feeds.size >= 3)
        assertTrue(feeds.all { !it.catalog })
        assertTrue(feeds.all { it.url.startsWith("https://") })
        assertTrue(feeds.all { it.url.contains("1405") })
        assertTrue(OccasionFeeds.holidayDayFeeds(1406).all { it.url.contains("1406") || it.url.contains("/1406") })
    }

    @Test
    fun `the shamsi-holidays shape is read per day`() {
        val payload = """
            [{"date":"1405-08-22","events":[{"description":"شهادت حضرت فاطمه زهرا (س)","is_holiday":true}],"is_holiday":true},
             {"date":"1405-08-23","events":[{"description":"روز جهانی دیابت","is_holiday":false}],"is_holiday":false},
             {"date":"۱۴۰۵/۰۴/۰۳","events":[{"description":"تاسوعا","is_holiday":true}],"is_holiday":true}]
        """.trimIndent()
        assertEquals(setOf(822, 403), HolidayDayParser.parse(payload))
    }

    @Test
    fun `the iran-holidays shape is read per day`() {
        val payload = """
            {"data":[{"date":"1405/01/01","isHoliday":true,"holidayDesription":"نوروز"},
                     {"date":"1405/01/05","isHoliday":false,"holidayDesription":null}]}
        """.trimIndent()
        assertEquals(setOf(101), HolidayDayParser.parse(payload))
    }

    @Test
    fun `malformed input yields no days instead of a wrong day`() {
        assertTrue(HolidayDayParser.parse("<html>blocked</html>").isEmpty())
        assertTrue(HolidayDayParser.parse("{\"data\":[]}").isEmpty())
        assertTrue(HolidayDayParser.parse("[{\"date\":\"nonsense\",\"is_holiday\":true}]").isEmpty())
    }

    @Test
    fun `a day needs two independent feeds before it is treated as corroborated`() {
        val crossCheck = HolidayCrossChecker.check(
            nationalHolidayDays = setOf(822, 403, 101),
            feedDays = listOf(setOf(822, 403), setOf(822, 101, 823)),
        )
        assertEquals(2, crossCheck.feeds)
        assertEquals(setOf(822), crossCheck.confirmed, "Only 22 Aban is confirmed by two feeds")
        assertEquals(setOf(823), crossCheck.communityOnly, "23 Aban is only a community-calendar claim")
        assertFalse(crossCheck.isEmpty)
        assertTrue(HolidayCrossChecker.check(emptySet(), emptyList()).isEmpty)
    }

    @Test
    fun `every occasion source reference is resolvable in the published feed`() {
        val catalog = requireNotNull(OccasionCatalog.parse(publishedFeedFile().readText()))
        val ids = catalog.sources.map { it.id }.toSet()
        assertTrue(catalog.occasions.all { occasion -> occasion.sourceIds.all { it in ids } })
        assertTrue(catalog.sources.all { it.url.startsWith("https://") && it.titleEn.isNotBlank() })
        assertTrue(catalog.sources.flatMap { listOf(it.tier) }.all { it in setOf("official", "unofficial", "open-source") })
    }
}
