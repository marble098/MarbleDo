package com.marbledo.domain

import com.marbledo.domain.model.Task
import com.marbledo.domain.model.AppSettings
import kotlinx.serialization.json.Json
import com.marbledo.domain.util.HiddenTapSequence
import com.marbledo.domain.util.PlanningInsights
import com.marbledo.domain.util.WorkdayPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlanningInsightsTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-10-11T09:00:00Z")
    private fun task(date: String?, done: Boolean = false, archived: Boolean = false, allDay: Boolean = true) = Task(
        title = "Task", dueAtEpochMillis = date?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() },
        isAllDay = allDay, isCompleted = done, isArchived = archived,
    )

    @Test fun `workload includes exactly seven civil dates and ignores archived tasks`() {
        val tasks = listOf(task("2026-10-11"), task("2026-10-11", done = true), task("2026-10-11", archived = true),
            task("2026-10-10"), task(null), task(null, done = true), task("2026-10-17"), task("2026-10-18"))
        val result = PlanningInsights.week(tasks, now, zone)
        assertEquals(7, result.days.size)
        assertEquals(1, result.days.first().open)
        assertEquals(1, result.days.first().completed)
        assertEquals(1, result.days.last().open)
        assertEquals(2, result.scheduledOpen)
        assertEquals(1, result.overdue)
        assertEquals(1, result.unscheduled)
    }

    @Test fun `today all-day task is not overdue but an earlier timed deadline is`() {
        val result = PlanningInsights.week(listOf(task("2026-10-11"), task("2026-10-11", allDay = false)), now, zone)
        assertEquals(1, result.overdue)
    }

    @Test fun `civil days remain correct at timezone and daylight saving edges`() {
        val instant = Instant.parse("2026-11-01T03:30:00Z")
        val tasks = listOf(Task(title = "Timed", dueAtEpochMillis = instant.toEpochMilli()))
        val result = PlanningInsights.week(tasks, instant, ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 10, 31), result.days.first().date)
        assertEquals(1, result.days.first().open)
        assertEquals(7, result.days.map { it.date }.toSet().size)
    }

    @Test fun `Friday and holiday chains are skipped across year boundaries`() {
        val holidays = setOf(LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 3))
        assertEquals(LocalDate.of(2027, 1, 4), WorkdayPlanner.nextWorkingDate(LocalDate.of(2026, 12, 31), holidays))
    }

    @Test fun `Thursday is opt-in and no available day has an explicit null result`() {
        val wednesday = LocalDate.of(2026, 10, 14)
        assertEquals(wednesday.plusDays(1), WorkdayPlanner.nextWorkingDate(wednesday, emptySet()))
        assertEquals(wednesday.plusDays(3), WorkdayPlanner.nextWorkingDate(wednesday, emptySet(), thursdayClosed = true))
        assertNull(WorkdayPlanner.nextWorkingDate(wednesday, emptySet(), thursdayClosed = true, maxSearchDays = 2))
        assertThrows(IllegalArgumentException::class.java) { WorkdayPlanner.nextWorkingDate(wednesday, emptySet(), maxSearchDays = 0) }
    }

    @Test fun `older settings profiles adopt hidden-off and Thursday-working defaults`() {
        val restored = Json.decodeFromString<AppSettings>("""{"languageTag":"fa"}""")
        assertFalse(restored.educationModeEnabled)
        assertFalse(restored.thursdayWeekendEnabled)
    }

    @Test fun `activation is explicit and resets after the time window`() {
        val gesture = HiddenTapSequence()
        (0L..5L).forEach { assertFalse(gesture.tap(it * 100)) }
        assertTrue(gesture.tap(600))
        assertFalse(gesture.tap(700))
        assertFalse(gesture.tap(10_000))
        (1L..5L).forEach { assertFalse(gesture.tap(10_000 + it)) }
        assertTrue(gesture.tap(10_006))
    }
}
