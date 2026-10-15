package com.marbledo.domain

import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.NotificationPlanBuilder
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationPlanTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun task(id: Long, time: String) = Task(id = id, title = "Task $id", dueAtEpochMillis = Instant.parse(time).toEpochMilli())

    @Test fun `overdue task cannot be hidden behind a future task and urgency breaks the tie`() {
        val tasks = listOf(task(1, "2026-10-16T12:00:00Z"), task(2, "2026-10-10T12:00:00Z"),
            task(3, "2026-10-14T12:00:00Z").copy(priority = TaskPriority.URGENT))
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        assertEquals(3L, plan.nextTask?.id)
        assertEquals(2, plan.overdueCount)
        assertTrue(plan.nextIsOverdue)
    }

    @Test fun `today all-day is not overdue and progress ignores archived and other-day completions`() {
        val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = listOf(Task(id = 1, title = "Flexible", dueAtEpochMillis = midnight, isAllDay = true),
            Task(title = "Done", dueAtEpochMillis = midnight, isAllDay = true, isCompleted = true),
            Task(title = "Archived", dueAtEpochMillis = midnight, isArchived = true),
            task(3, "2026-10-14T12:00:00Z").copy(isCompleted = true))
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        assertEquals(1, plan.todayOpen)
        assertEquals(1, plan.todayCompleted)
        assertEquals(2, plan.todayTotal)
        assertEquals(0, plan.overdueCount)
        assertFalse(plan.nextIsOverdue)
    }

    @Test fun `a finished or archived task never becomes the next actionable task`() {
        val tasks = listOf(task(1, "2026-10-14T12:00:00Z").copy(isCompleted = true),
            task(2, "2026-10-15T13:00:00Z").copy(isArchived = true), Task(title = "Undated"))
        assertNull(NotificationPlanBuilder.build(tasks, now, zone).nextTask)
    }

    @Test fun `future timed tasks are selected chronologically and no-task states stay empty`() {
        val plan = NotificationPlanBuilder.build(listOf(task(1, "2026-10-15T14:00:00Z"), task(2, "2026-10-15T13:00:00Z")), now, zone)
        assertEquals(2L, plan.nextTask?.id)
        assertNull(NotificationPlanBuilder.build(emptyList(), now, zone).nextTask)
    }
}
