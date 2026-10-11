package com.marbledo.feature.calendar

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionSearchAndTemplatesTest {
    private val reading = Occasion("reading", "روز کِتاب‌خوانی", "Reading Day", OccasionCategory.OFFICIAL,
        OccasionCalendar.JALALI, 8, 24, false, tags = setOf("education", "culture"))
    private val eid = Occasion("eid", "عید فطر", "Eid al-Fitr", OccasionCategory.RELIGIOUS,
        OccasionCalendar.LUNAR, 10, 1, true, tags = setOf("education"))
    private val rows = listOf(DatedOccasion(8, 24, reading, 1405), DatedOccasion(1, 1, eid, 1405))

    @Test fun `search is bilingual and normalizes Arabic letters ZWNJ and vowel marks`() {
        assertEquals(reading, OccasionSearch.filter(rows, OccasionFilter(query = "كتاب خواني"), emptySet()).single().occasion)
        assertEquals(reading, OccasionSearch.filter(rows, OccasionFilter(query = "READING"), emptySet()).single().occasion)
        assertTrue(OccasionSearch.filter(rows, OccasionFilter(query = "reading missing"), emptySet()).isEmpty())
    }

    @Test fun `filters intersect without claiming hidden categories are enabled`() {
        assertEquals(eid, OccasionSearch.filter(rows, OccasionFilter(holidaysOnly = true, calendar = OccasionCalendar.LUNAR), emptySet()).single().occasion)
        assertEquals(reading, OccasionSearch.filter(rows, OccasionFilter(culturalOnly = true, educationOnly = true), emptySet()).single().occasion)
        assertEquals(reading, OccasionSearch.filter(rows, OccasionFilter(favoritesOnly = true), setOf("reading")).single().occasion)
        assertTrue(OccasionSearch.filter(rows, OccasionFilter(category = OccasionCategory.EDUCATIONAL), emptySet()).isEmpty())
    }

    @Test fun `templates produce editable unsaved all-day tasks with unique checklist ids`() {
        val zone = ZoneId.of("Asia/Tehran")
        val request = OccasionPlanRequest("Reading Day", "2026-11-15", false,
            OccasionTemplates.checklist(PreparationTemplate.CULTURAL, "en"), "Source notes")
        val draft = OccasionTemplates.draft(request, zone)
        assertEquals(0L, draft.id)
        assertTrue(draft.isAllDay)
        assertFalse(draft.countdownEnabled)
        assertEquals(LocalDate.of(2026, 11, 15).atStartOfDay(zone).toInstant().toEpochMilli(), draft.dueAtEpochMillis)
        assertEquals(4, draft.checklist.size)
        assertEquals(4, draft.checklist.map { it.id }.distinct().size)
        assertTrue(draft.checklist.none { it.isDone })
        assertEquals("Source notes", draft.description)
    }

    @Test fun `variable school programs have no implicit date or countdown`() {
        val request = OccasionPlanRequest("انتخابات مدرسه", null, false,
            OccasionTemplates.checklist(PreparationTemplate.SCHOOL, "fa"))
        val draft = OccasionTemplates.draft(request)
        assertNull(draft.dueAtEpochMillis)
        assertFalse(draft.isAllDay)
        assertFalse(draft.countdownEnabled)
        assertEquals(6, draft.checklist.size)
        PreparationTemplate.entries.forEach { template ->
            assertEquals(OccasionTemplates.checklist(template, "fa").size, OccasionTemplates.checklist(template, "en").size)
        }
    }
}
