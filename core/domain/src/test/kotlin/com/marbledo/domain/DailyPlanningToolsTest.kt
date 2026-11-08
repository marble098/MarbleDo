package com.marbledo.domain

import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.BudgetVerdict
import com.marbledo.domain.util.DailyFocusPlanner
import com.marbledo.domain.util.FocusBudgetPlanner
import com.marbledo.domain.util.FocusReason
import com.marbledo.domain.util.GapFinder
import com.marbledo.domain.util.TaskAging
import com.marbledo.domain.util.WeekBalancePlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The five everyday planning tools: top picks, budget, gaps, aging and week balance. */
class DailyPlanningToolsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tehran")
    private val now: Instant = at(2026, 3, 14, 10, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private fun task(
        id: Long,
        title: String = "task $id",
        due: Instant? = null,
        allDay: Boolean = false,
        priority: TaskPriority = TaskPriority.NORMAL,
        pinned: Boolean = false,
        focusMinutes: Int = 0,
        createdAt: Instant = now.minusSeconds(3600),
        completed: Boolean = false,
    ) = Task(
        id = id,
        title = title,
        dueAtEpochMillis = due?.toEpochMilli(),
        isAllDay = allDay,
        priority = priority,
        isPinned = pinned,
        focusMinutes = focusMinutes,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        isCompleted = completed,
    )

    @Test
    fun `top picks put the overdue task first and explain why`() {
        val tasks = listOf(
            task(1, "later", due = at(2026, 3, 20, 9, 0)),
            task(2, "overdue", due = at(2026, 3, 13, 9, 0)),
            task(3, "today", due = at(2026, 3, 14, 18, 0)),
            task(4, "no date"),
        )
        val picks = DailyFocusPlanner.top(tasks, now, zone, limit = 3)
        assertEquals(listOf(2L, 3L, 1L), picks.map { it.task.id })
        assertEquals(FocusReason.OVERDUE, picks[0].reason)
        assertEquals(FocusReason.DUE_TODAY, picks[1].reason)
    }

    @Test
    fun `an all-day task due yesterday is overdue, an all-day task due today is not`() {
        val yesterday = task(1, "yesterday", due = at(2026, 3, 13, 0, 0), allDay = true)
        val today = task(2, "today", due = at(2026, 3, 14, 0, 0), allDay = true)
        val todayDate = LocalDate.of(2026, 3, 14)
        assertEquals(FocusReason.OVERDUE, DailyFocusPlanner.reason(yesterday, now, zone, todayDate))
        assertEquals(FocusReason.DUE_TODAY, DailyFocusPlanner.reason(today, now, zone, todayDate))
    }

    @Test
    fun `urgent wins over a distant due date and completed work is never picked`() {
        val tasks = listOf(
            task(1, "far", due = at(2026, 3, 30, 9, 0)),
            task(2, "urgent", priority = TaskPriority.URGENT),
            task(3, "done", priority = TaskPriority.URGENT, completed = true),
        )
        val picks = DailyFocusPlanner.top(tasks, now, zone, limit = 2)
        assertEquals(listOf(2L, 1L), picks.map { it.task.id })
        assertEquals(FocusReason.URGENT, picks[0].reason)
    }

    @Test
    fun `an unestimated task is planned as fifteen minutes, never zero`() {
        assertEquals(DailyFocusPlanner.DEFAULT_MINUTES, DailyFocusPlanner.minutesOf(task(1)))
        assertEquals(30, DailyFocusPlanner.minutesOf(task(2, focusMinutes = 30)))
    }

    @Test
    fun `budget compares planned effort with the minutes left in the day`() {
        val tasks = listOf(
            task(1, "a", due = at(2026, 3, 14, 12, 0), focusMinutes = 60),
            task(2, "b", due = at(2026, 3, 14, 15, 0), focusMinutes = 30),
            task(3, "tomorrow", due = at(2026, 3, 15, 9, 0), focusMinutes = 600),
        )
        val budget = FocusBudgetPlanner.today(tasks, now, zone, dayEndMinute = 18 * 60)
        assertEquals(90, budget.plannedMinutes)
        assertEquals(8 * 60, budget.freeMinutes)
        assertEquals(2, budget.openToday)
        assertEquals(BudgetVerdict.FITS, budget.verdict)
    }

    @Test
    fun `a plan longer than the remaining day is reported as over, not silently clipped`() {
        val tasks = listOf(
            task(1, "deep work", due = at(2026, 3, 14, 20, 0), focusMinutes = 300),
            task(2, "review", due = at(2026, 3, 14, 21, 0), focusMinutes = 120),
        )
        val budget = FocusBudgetPlanner.today(tasks, now, zone, dayEndMinute = 16 * 60)
        assertEquals(BudgetVerdict.OVER, budget.verdict)
        assertEquals(420, budget.plannedMinutes)
        assertTrue(budget.leftoverMinutes < 0)
    }

    @Test
    fun `the last minutes of the day leave no free time`() {
        val late = at(2026, 3, 14, 23, 30)
        assertEquals(0, FocusBudgetPlanner.freeMinutesLeft(late, zone, dayEndMinute = 22 * 60))
        assertEquals(BudgetVerdict.EMPTY, FocusBudgetPlanner.today(emptyList(), now, zone).verdict)
    }

    @Test
    fun `the next gap ends at the next timed task, not at an all-day one`() {
        val tasks = listOf(
            task(1, "all day", due = at(2026, 3, 14, 10, 30), allDay = true),
            task(2, "meeting", due = at(2026, 3, 14, 11, 0)),
        )
        val gap = GapFinder.nextGap(tasks, now, zone)
        assertNotNull(gap)
        assertEquals(60, gap!!.minutes)
        assertEquals(at(2026, 3, 14, 11, 0).toEpochMilli(), gap.endsAtEpochMillis)
    }

    @Test
    fun `a gap too short to use is not suggested`() {
        val tasks = listOf(task(1, "standup", due = at(2026, 3, 14, 10, 5)))
        assertNull(GapFinder.nextGap(tasks, now, zone))
    }

    @Test
    fun `the gap suggestion is the most urgent task that actually fits`() {
        val tasks = listOf(
            task(1, "too long", due = at(2026, 3, 13, 9, 0), focusMinutes = 90),
            task(2, "fits", due = at(2026, 3, 13, 9, 0), focusMinutes = 20),
            task(3, "quick", focusMinutes = 5),
        )
        val gap = GapFinder.nextGap(emptyList(), now, zone)!!
        val fit = GapFinder.fitting(tasks, gap.copy(minutes = 25), now, zone)
        assertEquals(2L, fit?.id)
    }

    @Test
    fun `aging counts only work that waited more than a week`() {
        val tasks = listOf(
            task(1, "old", createdAt = now.minusSeconds(40L * 86_400)),
            task(2, "week", createdAt = now.minusSeconds(8L * 86_400)),
            task(3, "fresh", createdAt = now.minusSeconds(2L * 86_400)),
            task(4, "done old", createdAt = now.minusSeconds(60L * 86_400), completed = true),
        )
        val summary = TaskAging.summary(tasks, now)
        assertEquals(2, summary.overWeek)
        assertEquals(1, summary.overMonth)
        assertEquals(1L, summary.oldest?.id)
        assertEquals(40L, summary.oldestDays)
        assertTrue(summary.nudging)
    }

    @Test
    fun `a quiet backlog does not nudge`() {
        val summary = TaskAging.summary(listOf(task(1, "fresh", createdAt = now.minusSeconds(86_400))), now)
        assertFalse(summary.nudging)
        assertEquals(0, summary.overWeek)
    }

    @Test
    fun `week balance finds the overloaded day and the calm one to move work to`() {
        val tasks = listOf(
            task(1, "sat 1", due = at(2026, 3, 15, 9, 0)),
            task(2, "sat 2", due = at(2026, 3, 15, 11, 0)),
            task(3, "sat 3", due = at(2026, 3, 15, 13, 0)),
            task(4, "sun", due = at(2026, 3, 16, 9, 0)),
            task(5, "completed", due = at(2026, 3, 15, 9, 0), completed = true),
        )
        val balance = WeekBalancePlanner.balance(tasks, now, zone)
        assertEquals(LocalDate.of(2026, 3, 15), balance.heaviestDay)
        assertEquals(3, balance.heaviestCount)
        // The calm day is the emptiest one after today (Sunday already has one task), which is
        // where work should move to.
        assertEquals(LocalDate.of(2026, 3, 17), balance.lightestDay)
        assertEquals(0, balance.lightestCount)
        assertTrue(balance.imbalanced)
    }

    @Test
    fun `an evenly loaded week is not reported as imbalanced`() {
        val tasks = (1..7).map { day -> task(day.toLong(), "t$day", due = at(2026, 3, 13 + day, 9, 0)) }
        val balance = WeekBalancePlanner.balance(tasks, now, zone)
        assertEquals(1, balance.heaviestCount)
        assertEquals(1, balance.lightestCount)
        assertFalse(balance.imbalanced)
    }
}
