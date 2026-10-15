package com.marbledo.domain

import com.marbledo.domain.model.Task
import com.marbledo.domain.util.ReviewAdvice
import com.marbledo.domain.util.WeeklyReviewPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WeeklyReviewTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val today = LocalDate.of(2026, 10, 15)
    private val now = today.atTime(12, 0).atZone(zone).toInstant()
    private fun task(daysAgo: Long, done: Boolean = false) = Task(title = "Task", isAllDay = true,
        dueAtEpochMillis = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli(), isCompleted = done)

    @Test fun `review uses seven complete civil days and never counts today's unfinished day`() {
        val tasks = listOf(task(1, true), task(7), task(8, true), task(14), task(15, true), task(0, true),
            task(2, true).copy(isArchived = true), Task(title = "Undated"))
        val review = WeeklyReviewPlanner.review(tasks, now, zone)
        assertEquals(2, review.recent.scheduled)
        assertEquals(1, review.recent.completed)
        assertEquals(2, review.previous.scheduled)
        assertEquals(50, review.recent.completionPercent)
        assertEquals(0, review.changePercentagePoints)
        assertEquals(today.minusDays(7), review.days.first().date)
        assertEquals(today.minusDays(1), review.days.last().date)
        assertEquals(7, review.days.size)
        assertEquals(1, review.unscheduledTasks.size)
        assertEquals(ReviewAdvice.CLEAR_BACKLOG, review.advice)
    }

    @Test fun `empty weeks have no fabricated zero percent or invented trend`() {
        val review = WeeklyReviewPlanner.review(emptyList(), now, zone)
        assertNull(review.recent.completionPercent)
        assertNull(review.changePercentagePoints)
        assertEquals(ReviewAdvice.START_PLANNING, review.advice)
    }

    @Test fun `focus minutes remain estimates and completion is classified by due date not updatedAt`() {
        val task = task(3, true).copy(focusMinutes = 45, updatedAtEpochMillis = now.toEpochMilli())
        val review = WeeklyReviewPlanner.review(listOf(task), now, zone)
        assertEquals(45L, review.recent.plannedFocusMinutes)
        assertEquals(1, review.recent.completed)
        assertEquals(0, review.previous.completed)
    }

    @Test fun `large imported focus estimates do not overflow into a negative total`() {
        val review = WeeklyReviewPlanner.review(listOf(task(1).copy(focusMinutes = Int.MAX_VALUE), task(2).copy(focusMinutes = Int.MAX_VALUE)), now, zone)
        assertEquals(2L * Int.MAX_VALUE, review.recent.plannedFocusMinutes)
    }

    @Test fun `today all-day is not overdue but timed deadline earlier today is`() {
        val review = WeeklyReviewPlanner.review(listOf(task(0), task(0).copy(isAllDay = false), task(-6), task(-7)), now, zone)
        assertEquals(1, review.overdueTasks.size)
        assertEquals(3, review.nextWeekOpen)
    }

    @Test fun `undated backlog is actionable and archived tasks never influence advice`() {
        val review = WeeklyReviewPlanner.review(listOf(Task(title = "Later"), task(1).copy(isArchived = true)), now, zone)
        assertEquals(ReviewAdvice.SCHEDULE_UNDATED, review.advice)
        assertTrue(review.overdueTasks.isEmpty())
    }

    @Test fun `a much heavier upcoming week produces actionable load advice even after a completed week`() {
        val past = listOf(task(1, true), task(2, true))
        val future = (0L..5L).map { task(-it) }
        val review = WeeklyReviewPlanner.review(past + future, now, zone)
        assertTrue(review.overdueTasks.isEmpty())
        assertEquals(100, review.recent.completionPercent)
        assertEquals(ReviewAdvice.REDUCE_LOAD, review.advice)
        assertEquals(6, review.nextWeekOpen)
    }

    @Test fun `week boundaries follow local dates through DST and year changes`() {
        val review = WeeklyReviewPlanner.review(emptyList(), Instant.parse("2027-01-01T00:30:00Z"), ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 12, 24), review.recent.start)
        assertEquals(LocalDate.of(2026, 12, 30), review.recent.end)
        assertEquals(7, review.days.map { it.date }.distinct().size)
    }
}
