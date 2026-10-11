package com.marbledo.feature.habits

import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitDifficulty
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitSchedule
import com.marbledo.domain.model.HabitScheduleType
import com.marbledo.domain.model.HabitTracking
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rules that do not need a device: validation and the reminder clock. */
class HabitRulesTest {

    private val noon = ZonedDateTime.of(2026, 10, 11, 12, 0, 0, 0, ZoneId.of("Asia/Tehran"))

    @Test
    fun `a reminder time later today is scheduled for today`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(18 * 60, noon)
        assertEquals(ZonedDateTime.of(2026, 10, 11, 18, 0, 0, 0, ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli(), trigger)
    }

    @Test
    fun `a reminder time already passed rolls to tomorrow`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(7 * 60, noon)
        assertEquals(ZonedDateTime.of(2026, 10, 12, 7, 0, 0, 0, ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli(), trigger)
    }

    @Test
    fun `a habit needs a name`() {
        assertThrows(IllegalArgumentException::class.java) { Habit(name = "  ") }
    }

    @Test
    fun `the daily target stays inside a sane range`() {
        assertThrows(IllegalArgumentException::class.java) { Habit(name = "Water", dailyTarget = 0) }
        assertThrows(IllegalArgumentException::class.java) { Habit(name = "Water", dailyTarget = 100) }
        assertEquals(8, Habit(name = "Water", dailyTarget = 8).dailyTarget)
    }

    @Test
    fun `a weekly target is between one and seven`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitSchedule(type = HabitScheduleType.TIMES_PER_WEEK, timesPerWeek = 9)
        }
    }

    @Test
    fun `a quit habit is always a single tick`() {
        val quit = Habit(name = "No late snacks", kind = HabitKind.QUIT, tracking = HabitTracking.COUNT)
        assertEquals(HabitTracking.COUNT, quit.tracking)
        assertEquals(1, quit.dailyTarget)
    }

    @Test
    fun `days are only stored as ISO dates`() {
        assertThrows(IllegalArgumentException::class.java) { HabitEntry(1, "11/10/2026", 1) }
        assertEquals(1, HabitEntry(1, "2026-10-11", 1).value)
    }

    @Test
    fun `a specific-days schedule knows which days it asks for`() {
        val schedule = HabitSchedule.weekdays()
        assertTrue(schedule.isScheduled(java.time.LocalDate.parse("2026-10-08"))) // Thursday
        assertFalse(schedule.isScheduled(java.time.LocalDate.parse("2026-10-10"))) // Saturday
    }

    @Test
    fun `difficulty is recorded but never changes the target`() {
        val habit = Habit(name = "Run", difficulty = HabitDifficulty.HARD, dailyTarget = 2)
        assertEquals(HabitDifficulty.HARD, habit.difficulty)
        assertEquals(2, habit.dailyTarget)
    }
}
