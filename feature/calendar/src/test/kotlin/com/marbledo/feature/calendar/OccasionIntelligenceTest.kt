package com.marbledo.feature.calendar

import com.marbledo.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionIntelligenceTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val date = LocalDate.of(2026, 12, 25)
    private val occasion = Occasion("christmas", "کریسمس", "Christmas", OccasionCategory.PERSONAL, OccasionCalendar.GREGORIAN, 12, 25, false)
    private fun upcoming(id: String, days: Long, favorite: Boolean = false) = UpcomingOccasion(occasion.copy(id = id), 1405, 10, 4,
        date.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli(), days, favorite)

    @Test fun `favorites lead with an explanation while remaining rows are chronological`() {
        val result = OccasionIntelligence.recommendations(listOf(upcoming("later", 12, true), upcoming("soon", 2), upcoming("today", 0)), emptyList(), zone)
        assertEquals(listOf("later", "today", "soon"), result.map { it.upcoming.occasion.id })
        assertEquals(OccasionRecommendationReason.FAVORITE, result.first().reason)
        assertEquals(OccasionRecommendationReason.TODAY, result[1].reason)
        assertEquals(7, result.first().preparationDays)
        assertEquals(0, result[1].preparationDays)
    }

    @Test fun `horizon excludes past and distant rows and repeated annual identities are not duplicated`() {
        val result = OccasionIntelligence.recommendations(listOf(upcoming("same", 3), upcoming("same", 10), upcoming("past", -1), upcoming("far", 31)), emptyList(), zone)
        assertEquals(1, result.size)
        assertEquals(3L, result.single().upcoming.daysUntil)
        assertTrue(OccasionIntelligence.recommendations(emptyList(), emptyList(), zone, limit = 0).isEmpty())
    }

    @Test fun `saved plan tags survive renamed tasks but are scoped to the actual occurrence year`() {
        val tagged = Task(title = "My renamed plan", tags = listOf(OccasionIntelligence.planningTag(occasion.id, date)), isCompleted = true)
        assertTrue(OccasionIntelligence.isPlanned(occasion, date, listOf(tagged), zone))
        assertFalse(OccasionIntelligence.isPlanned(occasion, date.plusYears(1), listOf(tagged), zone))
        assertFalse(OccasionIntelligence.isPlanned(occasion, date, listOf(tagged.copy(isArchived = true)), zone))
    }

    @Test fun `legacy bilingual tasks on the occasion date are recognized without mutating them`() {
        val task = Task(title = "کریسمس", dueAtEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(OccasionIntelligence.isPlanned(occasion, date, listOf(task), zone))
        assertTrue(task.tags.isEmpty())
        assertFalse(OccasionIntelligence.isPlanned(occasion, date.plusDays(1), listOf(task), zone))
    }

    @Test fun `one-tap plans keep the occasion link even when preparation falls on an earlier date`() {
        val epoch = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val request = OccasionPlanner.planRequest(occasion, epoch, "en", daysBefore = 7, zone = zone)
        val draft = OccasionTemplates.draft(request, zone)
        assertEquals(date.minusDays(7).toString(), request.dateIso)
        assertTrue(OccasionIntelligence.planningTag(occasion.id, date) in draft.tags)
    }
}
