package com.marbledo.feature.calendar

import com.marbledo.domain.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreparationTimelineTest {
    private val eventDate = LocalDate.of(2026, 12, 25)
    private val today = LocalDate.of(2026, 12, 1)
    private val zone = ZoneId.of("America/New_York")
    private val occasion = Occasion("christmas", "کریسمس", "Christmas", OccasionCategory.PERSONAL, OccasionCalendar.GREGORIAN, 12, 25, false)

    @Test fun `timeline proposes four independent stages and never moves the occasion itself`() {
        val steps = PreparationTimeline.suggest(occasion, eventDate, today, emptyList(), "en", thursdayClosed = true)
        assertEquals(4, steps.size)
        assertEquals(eventDate, steps.last().date)
        assertEquals(DayOfWeek.FRIDAY, steps.last().date.dayOfWeek)
        assertTrue(steps.dropLast(1).none { it.date.dayOfWeek in setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) })
        assertTrue(steps.none { it.isPast || it.alreadyExists })
    }

    @Test fun `preparation walks backward across a holiday chain and preserves the nominal date`() {
        val nominal = eventDate.minusDays(1)
        val holidays = setOf(nominal, nominal.minusDays(1))
        val step = PreparationTimeline.suggest(occasion, eventDate, today, emptyList(), "en", holidays)
            .single { it.stage == PreparationStage.PREPARE }
        assertEquals(nominal, step.originalDate)
        assertEquals(nominal.minusDays(2), step.date)
        assertTrue(step.date < eventDate)
    }

    @Test fun `past stages are explicitly marked not silently scheduled in the past`() {
        val steps = PreparationTimeline.suggest(occasion, eventDate, eventDate.minusDays(2), emptyList(), "fa")
        assertTrue(steps.first().isPast)
        assertFalse(steps.last().isPast)
        val untouched = PreparationTimeline.suggest(occasion, eventDate, today, emptyList(), "en", avoidNonWorkingDays = false)
        assertEquals(eventDate.minusDays(14), untouched.first().date)
    }

    @Test fun `stage identity survives editing and does not suppress next year's preparation`() {
        val tag = PreparationTimeline.stageTag(occasion.id, eventDate, PreparationStage.PLAN)
        val saved = Task(title = "Renamed", tags = listOf(tag), isCompleted = true)
        val steps = PreparationTimeline.suggest(occasion, eventDate, today, listOf(saved), "en")
        assertTrue(steps.first().alreadyExists)
        assertFalse(steps[1].alreadyExists)
        assertFalse(PreparationTimeline.suggest(occasion, eventDate.plusYears(1), today, listOf(saved), "en").first().alreadyExists)
    }

    @Test fun `draft is unsaved local all-day task with stable link checklist and explicit focus estimate`() {
        val step = PreparationTimeline.suggest(occasion, eventDate, today, emptyList(), "en").single { it.stage == PreparationStage.PREPARE }
        val task = PreparationTimeline.draft(occasion, eventDate, step, "en", zone)
        assertEquals(0L, task.id)
        assertEquals(step.date.atStartOfDay(zone).toInstant().toEpochMilli(), task.dueAtEpochMillis)
        assertTrue(task.isAllDay)
        assertFalse(task.countdownEnabled)
        assertEquals(45, task.focusMinutes)
        assertEquals(2, task.tags.size)
        assertEquals(3, task.checklist.size)
        assertTrue(task.checklist.none { it.isDone })
    }

    @Test fun `a fully closed search window is reported rather than silently called a working day`() {
        val holidays = (1L..30L).map { eventDate.minusDays(it) }.toSet()
        val steps = PreparationTimeline.suggest(occasion, eventDate, today.minusMonths(1), emptyList(), "en", holidays)
        assertTrue(steps.dropLast(1).none { it.workingDateFound })
        assertTrue(steps.last().workingDateFound)
    }

    @Test fun `an existing simple occasion-day task prevents duplicating the day stage only`() {
        val saved = Task(title = "Christmas", dueAtEpochMillis = eventDate.atStartOfDay(zone).toInstant().toEpochMilli())
        val steps = PreparationTimeline.suggest(occasion, eventDate, today, listOf(saved), "en", zone = zone)
        assertTrue(steps.last().alreadyExists)
        assertTrue(steps.dropLast(1).none { it.alreadyExists })
    }

    @Test fun `only the occasion stage becomes a live countdown and leap-year dates stay civil`() {
        val leap = LocalDate.of(2028, 2, 29)
        val step = PreparationTimeline.suggest(occasion, leap, today, emptyList(), "en").last()
        assertEquals(leap, step.date)
        assertTrue(PreparationTimeline.draft(occasion, leap, step, "en", zone).countdownEnabled)
    }
}
