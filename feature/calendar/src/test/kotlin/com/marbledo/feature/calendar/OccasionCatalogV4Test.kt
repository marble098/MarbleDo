package com.marbledo.feature.calendar

import com.marbledo.domain.model.AppSettings
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionCatalogV4Test {
    private val asset by lazy {
        listOf(File("src/main/assets/calendar/holidays-fa.json"), File("feature/calendar/src/main/assets/calendar/holidays-fa.json"))
            .first { it.exists() }.readText()
    }
    private val catalog by lazy { requireNotNull(OccasionCatalog.parse(asset)) }
    private val legacy = """{"holidays":[{"month":1,"day":1,"titleFa":"نوروز","titleEn":"Nowruz","category":"national","holiday":true}]}"""

    @Test fun `modern asset parses with complete references and stable correction identities`() {
        assertEquals(4, catalog.schemaVersion)
        assertTrue(catalog.occasions.size >= 800)
        assertTrue(catalog.schoolPrograms.size >= 20)
        val ids = catalog.sources.map { it.id }.toSet()
        assertTrue(catalog.occasions.all { it.sourceIds.isNotEmpty() && it.sourceIds.all { id -> id in ids } })
        val education = catalog.occasions.single { it.titleEn == "Islamic Education Affairs Day" }
        assertEquals(12, education.month)
        assertEquals(8, education.day)
        assertEquals("jalali-12-24-Islamic Education Affairs Day", education.id)
    }

    @Test fun `old cache schema remains readable but cannot supersede this richer bundle`() {
        val old = OccasionCatalog.parse(legacy)!!
        assertEquals("jalali-1-1-Nowruz", old.occasions.single().id)
        assertFalse(CatalogUpdatePolicy.accepts(old, catalog))
        assertNotNull(OccasionCatalog.parse(legacy.replace("holidays", "jalali")))
    }

    @Test fun `bad data rejects the complete response instead of quietly deleting entries`() {
        assertNull(OccasionCatalog.parse("<html>error</html>"))
        assertNull(OccasionCatalog.parse(asset.replace("\"schemaVersion\": 4", "\"schemaVersion\": 99")))
        assertNull(OccasionCatalog.parse(legacy.replace("\"day\":1", "\"day\":0")))
        assertNull(OccasionCatalog.parse(legacy.replace("\"national\"", "\"unknown\"")))
        assertNull(OccasionCatalog.parse(legacy.replace("\"day\":1", "\"day\":31").replace("\"month\":1", "\"month\":7")))
        assertNull(OccasionCatalog.parse(asset.replace("\"sourceIds\": [", "\"sourceIds\": [\"missing-source\",", ignoreCase = false)))
        assertNull(OccasionCatalog.parse(asset.replace("\"dateRule\": \"month_end\"", "\"dateRule\": \"unknown\"")))
    }

    @Test fun `stale mirrors truncated files lost categories and lost program lists are rejected`() {
        assertTrue(CatalogUpdatePolicy.accepts(catalog, catalog))
        assertFalse(CatalogUpdatePolicy.accepts(catalog.copy(dataVersion = "5"), catalog))
        assertFalse(CatalogUpdatePolicy.accepts(catalog.copy(dataVersion = null), catalog))
        assertFalse(CatalogUpdatePolicy.accepts(catalog.copy(occasions = catalog.occasions.take(30)), catalog))
        assertFalse(CatalogUpdatePolicy.accepts(catalog.copy(schoolPrograms = emptyList()), catalog))
        assertFalse(CatalogUpdatePolicy.accepts(catalog.copy(occasions = catalog.occasions.filterNot { it.category == OccasionCategory.EDUCATIONAL }), catalog))
    }

    @Test fun `educational layer is opt-in and uses the same category gate as all public surfaces`() {
        val default = AppSettings()
        assertFalse(default.educationModeEnabled)
        assertFalse(OccasionCategory.EDUCATIONAL in default.enabledOccasionCategories())
        assertEquals(OccasionCategory.publicCategories, default.enabledOccasionCategories())
        assertTrue(OccasionCategory.EDUCATIONAL in default.copy(educationModeEnabled = true).enabledOccasionCategories())
        assertFalse(OccasionCategory.RELIGIOUS in default.copy(religiousEventsEnabled = false, educationModeEnabled = true).enabledOccasionCategories())
    }

    @Test fun `favorites migrate removed duplicates while surviving moved dates`() {
        val oldId = "jalali-3-22-Girls' Day"
        val canonical = catalog.occasions.single { it.titleEn == "Birth of Lady Masouma and Girls' Day" }.id
        assertEquals(setOf(canonical, "missing"), catalog.canonicalFavoriteIds(listOf(oldId, canonical, "missing")))
        val corrected = catalog.occasions.single { it.titleEn == "Death of Lady Ruqayya (traditional report)" }
        assertEquals(2, corrected.month)
        assertEquals("lunar-1-5-Martyrdom of Lady Ruqayya", corrected.id)
    }

    @Test fun `one-year election dates never become annual and guidance never becomes a holiday`() {
        assertEquals(setOf(1404), catalog.yearly.keys)
        val elections = catalog.yearly.getValue(1404).occasions.single()
        assertEquals(8, elections.month)
        assertEquals(20, elections.day)
        assertEquals(OccasionCategory.EDUCATIONAL, elections.category)
        assertFalse(catalog.occasions.any { it.titleEn.contains("Elections") })
        assertTrue(catalog.occasions.filter { it.evidence == OccasionEvidence.GUIDANCE }.all { !it.isHoliday })
        assertTrue(catalog.schoolPrograms.all { it.sourceIds.isNotEmpty() })
    }
}
