package com.marbledo.feature.calendar

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.OccasionCulture
import com.marbledo.domain.model.enabledOccasionCultures
import java.io.File
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionCatalogV5Test {
    private val asset by lazy {
        listOf(File("src/main/assets/calendar/occasions-v5-fa.json"), File("feature/calendar/src/main/assets/calendar/occasions-v5-fa.json"))
            .first { it.exists() }.readText()
    }
    private val catalog by lazy { requireNotNull(OccasionCatalog.parse(asset)) }

    @Test fun `v5 adds a referenced Western calendar without changing existing stable ids`() {
        assertEquals(5, catalog.schemaVersion)
        assertTrue(catalog.occasions.size >= 990)
        assertEquals(131, catalog.occasions.count { it.id.startsWith("western-") })
        assertTrue(catalog.occasions.all { it.cultures.isNotEmpty() })
        assertEquals(OccasionCulture.all, catalog.occasions.flatMap { it.cultures }.toSet())
        val old = listOf(File("src/main/assets/calendar/occasions-v4-fa.json"), File("feature/calendar/src/main/assets/calendar/occasions-v4-fa.json"))
            .first { it.exists() }.readText().let(OccasionCatalog::parse)!!
        assertTrue(catalog.occasions.map { it.id }.containsAll(old.occasions.map { it.id }))
        assertFalse(CatalogUpdatePolicy.accepts(old, catalog))
    }

    @Test fun `floating Western dates are rules rather than stale hard-coded dates`() {
        fun event(title: String) = catalog.occasions.single { it.titleEn == title }
        assertEquals(OccasionDateRule.WESTERN_EASTER, event("Western Easter Sunday").dateRule)
        assertEquals(OccasionDateRule.ORTHODOX_EASTER, event("Orthodox Easter Sunday").dateRule)
        assertEquals(4, event("Thanksgiving (US)").ordinal)
        assertEquals(2, event("Thanksgiving (Canada)").ordinal)
        assertEquals(OccasionDateRule.WEEKDAY_ON_OR_BEFORE, event("Advent Sunday 1").dateRule)
        assertEquals(OccasionDateRule.SUNDAY_TO_SATURDAY, event("Kings Day (Netherlands)").dateRule)
        assertEquals(OccasionDateRule.LAST_WEEKDAY, event("World Maritime Day").dateRule)
        assertEquals(OccasionDateRule.NTH_WEEKDAY, event("World Habitat Day").dateRule)
        assertTrue(catalog.occasions.filter { OccasionCulture.IRANIAN !in it.cultures }.none { it.isHoliday })
    }

    @Test fun `culture and category gates intersect and empty selection is intentionally empty`() {
        assertEquals(OccasionCulture.all, AppSettings().enabledOccasionCultures())
        val defaults = AppSettings()
        val western = OccasionVisibility.forYear(catalog, 1405, defaults.enabledOccasionCategories(), setOf(OccasionCulture.WESTERN))
        assertTrue(western.isNotEmpty())
        assertTrue(western.all { OccasionCulture.WESTERN in it.cultures })
        assertTrue(western.none { it.category == OccasionCategory.EDUCATIONAL })
        val noReligion = OccasionVisibility.forYear(catalog, 1405, defaults.copy(religiousEventsEnabled = false).enabledOccasionCategories(), setOf(OccasionCulture.WESTERN))
        assertTrue(noReligion.none { it.category == OccasionCategory.RELIGIOUS })
        assertTrue(OccasionVisibility.forYear(catalog, 1405, OccasionCategory.entries.toSet(), emptySet()).isEmpty())
        assertTrue(OccasionIndex.build(catalog, listOf(1405), 0, enabledCultures = emptySet()).isEmpty)
    }

    @Test fun `a yearly replacement with the same id supersedes the recurring date`() {
        val base = catalog.occasions.first()
        val replacement = base.copy(day = if (base.day == 1) 2 else 1)
        val changed = catalog.copy(yearly = mapOf(1405 to YearlyOverride(listOf(replacement))))
        val result = OccasionVisibility.forYear(changed, 1405, OccasionCategory.entries.toSet(), OccasionCulture.all)
        assertEquals(replacement.day, result.single { it.id == base.id }.day)
    }

    @Test fun `malformed culture arrays foreign closures and non-Gregorian Easter reject the entire feed`() {
        fun invalid(change: (JSONObject) -> Unit): OccasionCatalog? {
            val root = JSONObject(asset)
            change(root)
            return OccasionCatalog.parse(root.toString())
        }
        assertNull(invalid { it.getJSONArray("gregorian").getJSONObject(0).remove("cultures") })
        assertNull(invalid { it.getJSONArray("gregorian").getJSONObject(0).put("cultures", org.json.JSONArray()) })
        assertNull(invalid { it.getJSONArray("gregorian").getJSONObject(0).put("cultures", org.json.JSONArray(listOf("unknown"))) })
        assertNull(invalid { it.getJSONArray("gregorian").getJSONObject(0).put("holiday", true) })
        assertNull(invalid { it.getJSONArray("jalali").getJSONObject(0).put("dateRule", "western_easter") })
    }

    @Test fun `updates cannot silently drop an entire culture pack`() {
        val missing = catalog.copy(occasions = catalog.occasions.filterNot { OccasionCulture.OCEANIA in it.cultures })
        assertFalse(CatalogUpdatePolicy.accepts(missing, catalog))
    }

    @Test fun `culture filter composes with bilingual search and favorites`() {
        val rows = catalog.occasions.filter { it.calendar == OccasionCalendar.GREGORIAN }.map { DatedOccasion(it.month, it.day, it, 1405) }
        val result = OccasionSearch.filter(rows, OccasionFilter(query = "thanksgiving", culture = OccasionCulture.NORTH_AMERICA), emptySet())
        assertEquals(setOf("Thanksgiving (US)", "Thanksgiving (Canada)"), result.map { it.occasion.titleEn }.toSet())
    }
}
