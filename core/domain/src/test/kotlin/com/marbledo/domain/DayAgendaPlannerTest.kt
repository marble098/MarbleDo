package com.marbledo.domain

import com.marbledo.domain.model.Task
import com.marbledo.domain.util.DayAgendaPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DayAgendaPlannerTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val date = LocalDate.of(2026, 10, 15)
    private val now = date.atTime(8, 0).atZone(zone).toInstant()
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant()
    private fun task(id: Long, hour: Int, minute: Int = 0, duration: Int = 30) =
        Task(id = id, title = "Task $id", dueAtEpochMillis = at(hour, minute).toEpochMilli(), focusMinutes = duration)

    @Test fun `a deadline reserves the estimated block BEFORE it and slots do not overlap it`() {
        val plan = DayAgendaPlanner.plan(listOf(task(1, 10)), date, now, zone, 60)
        assertEquals(at(10), plan.freeSlots.first().start)
        assertEquals(at(11), plan.freeSlots.first().end)
        assertEquals(30, plan.estimatedMinutes)
    }

    @Test fun `overlapping intervals merge for free-time purposes and touching intervals are not conflicts`() {
        val tasks = listOf(task(1, 10), task(2, 10, 15), task(3, 10, 45))
        val plan = DayAgendaPlanner.plan(tasks, date, now, zone, 45)
        assertEquals(1, plan.conflicts.size)
        assertEquals(1L, plan.conflicts.single().first.id)
        assertEquals(2L, plan.conflicts.single().second.id)
        assertEquals(at(10, 45), plan.freeSlots.last().start)
    }

    @Test fun `completed archived all-day and undated tasks do not occupy timed slots`() {
        val tasks = listOf(task(1, 10).copy(isCompleted = true), task(2, 11).copy(isArchived = true),
            task(3, 12).copy(isAllDay = true), Task(title = "Undated"))
        val plan = DayAgendaPlanner.plan(tasks, date, now, zone)
        assertEquals(at(9), plan.freeSlots.single().start)
        assertEquals(1, plan.allDayTasks.size)
        assertEquals(0, plan.estimatedMinutes)
    }

    @Test fun `today never suggests the past and rounds up to a usable quarter hour`() {
        val plan = DayAgendaPlanner.plan(emptyList(), date, at(9, 7), zone)
        assertEquals(at(9, 15), plan.freeSlots.single().start)
        assertTrue(DayAgendaPlanner.plan(emptyList(), date, at(19), zone).freeSlots.isEmpty())
    }

    @Test fun `a cross-midnight block ending tomorrow still blocks today`() {
        val tomorrow = date.plusDays(1).atTime(0, 30).atZone(zone).toInstant()
        val task = Task(title = "Night", dueAtEpochMillis = tomorrow.toEpochMilli(), focusMinutes = 8 * 60)
        val plan = DayAgendaPlanner.plan(listOf(task), date, now, zone, 30)
        assertEquals(at(9), plan.freeSlots.single().start)
        assertTrue(plan.freeSlots.all { it.end <= at(16, 30) })
        assertEquals(1, plan.timedTasks.size)
    }

    @Test fun `self exclusion allows reviewing a replacement slot without changing saved data`() {
        val tasks = listOf(task(9, 10, duration = 120))
        val plan = DayAgendaPlanner.plan(tasks, date, now, zone, excludedTaskId = 9)
        assertEquals(at(9), plan.freeSlots.single().start)
        assertEquals(120, tasks.single().focusMinutes)
    }

    @Test fun `DST work windows are civil boundaries but durations are elapsed minutes`() {
        val ny = ZoneId.of("America/New_York")
        val fallBack = LocalDate.of(2026, 11, 1)
        val plan = DayAgendaPlanner.plan(emptyList(), fallBack, Instant.parse("2026-11-01T00:00:00Z"), ny,
            120, LocalTime.of(1, 0), LocalTime.of(3, 0))
        assertEquals(120L, java.time.Duration.between(plan.freeSlots.single().start, plan.freeSlots.single().end).toMinutes())
        assertTrue(plan.freeSlots.single().end <= fallBack.atTime(3, 0).atZone(ny).toInstant())
    }

    @Test fun `invalid durations and inverted windows are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DayAgendaPlanner.plan(emptyList(), date, now, zone, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            DayAgendaPlanner.plan(emptyList(), date, now, zone, workStart = LocalTime.NOON, workEnd = LocalTime.of(9, 0))
        }
    }
}
