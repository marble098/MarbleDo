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

    @Test fun `notification previews are bounded without splitting an emoji surrogate pair`() {
        val short = "Plan 🎉"
        assertEquals(short, NotificationPlanBuilder.previewTitle(short))
        val long = "🎉".repeat(300)
        val preview = NotificationPlanBuilder.previewTitle(long)
        assertEquals(160, preview.codePointCount(0, preview.length))
        assertTrue(preview.endsWith("…"))
        assertEquals("🎉".repeat(159) + "…", preview)
    }

    @Test fun `future timed tasks are selected chronologically and no-task states stay empty`() {
        val plan = NotificationPlanBuilder.build(listOf(task(1, "2026-10-15T14:00:00Z"), task(2, "2026-10-15T13:00:00Z")), now, zone)
        assertEquals(2L, plan.nextTask?.id)
        assertNull(NotificationPlanBuilder.build(emptyList(), now, zone).nextTask)
    }

    @Test fun `the overdue list reads most urgent first and never repeats a task in today`() {
        // Three late tasks with different priorities and due times, plus one that is merely due
        // later today. The grouped reminder shows the list in this order, so it is a contract.
        val tasks = listOf(
            task(1, "2026-10-13T12:00:00Z"),
            task(2, "2026-10-10T12:00:00Z").copy(priority = TaskPriority.URGENT),
            task(3, "2026-10-14T12:00:00Z"),
            task(4, "2026-10-15T18:00:00Z"),
        )
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        assertEquals(listOf(2L, 1L, 3L), plan.overdueTasks.map { it.id })
        assertEquals(3, plan.overdueCount)
        // The later-today task is the only one in today's list; the three late ones are not
        // repeated there, because they already own a row in the overdue group.
        assertEquals(listOf(4L), plan.todayTasks.map { it.id })
        // Only the later-today task belongs to today; the three late ones are counted once, by
        // overdueCount, and the chip's two numbers never overlap.
        assertEquals(1, plan.todayOpen)
    }

    @Test fun `the grouped summary counts what it hides`() {
        val tasks = (1..5).map { daysAgo ->
            Task(
                id = daysAgo.toLong(),
                title = "Late $daysAgo",
                dueAtEpochMillis = now.toEpochMilli() - daysAgo * 86_400_000L,
            )
        }
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        assertEquals(5, plan.overdueCount)
        assertEquals(2, plan.hiddenOverdueCount(visible = 3))
        assertEquals(0, plan.hiddenOverdueCount(visible = 5))
        assertEquals(0, plan.hiddenOverdueCount(visible = 9))
    }

    @Test fun `an empty day has no lists and no counts to render`() {
        val plan = NotificationPlanBuilder.build(emptyList(), now, zone)
        assertTrue(plan.overdueTasks.isEmpty())
        assertTrue(plan.todayTasks.isEmpty())
        assertEquals(0, plan.overdueCount)
        assertEquals(0, plan.todayOpen)
    }
}
