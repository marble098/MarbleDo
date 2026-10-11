package com.marbledo.domain

import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitDifficulty
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitSchedule
import com.marbledo.domain.model.HabitScheduleType
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.HabitDayState
import com.marbledo.domain.util.HabitInsightKey
import com.marbledo.domain.util.HabitPhase
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HabitAnalyticsTest {

    private val today = LocalDate.parse("2026-10-11")
    private val habit = Habit(
        id = 1,
        name = "Read ten pages",
        startDate = "2026-09-01",
        createdAtEpochMillis = 0L,
    )

    private fun entries(vararg dates: String) = dates.map { HabitEntry(1, it, 1) }

    @Test
    fun `streak counts consecutive done days`() {
        val values = HabitAnalytics.entriesByDate(entries("2026-10-09", "2026-10-10", "2026-10-11"))
        assertEquals(3, HabitAnalytics.currentStreak(habit, values, today, LocalDate.parse("2026-09-01")))
    }

    @Test
    fun `an unfinished today keeps yesterday's streak alive`() {
        val values = HabitAnalytics.entriesByDate(entries("2026-10-09", "2026-10-10"))
        val start = LocalDate.parse("2026-09-01")
        assertEquals(2, HabitAnalytics.currentStreak(habit, values, today, start))
        val stats = HabitAnalytics.stats(habit, entries("2026-10-09", "2026-10-10"), today)
        assertFalse(stats.todayDone)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun `a gap ends the streak`() {
        val values = HabitAnalytics.entriesByDate(entries("2026-10-08", "2026-10-11"))
        assertEquals(1, HabitAnalytics.currentStreak(habit, values, today, LocalDate.parse("2026-09-01")))
    }

    @Test
    fun `unscheduled days never break a streak`() {
        val weekdays = HabitSchedule.weekdays()
        val scheduled = Habit(id = 2, name = "Gym", schedule = weekdays, startDate = "2026-09-01")
        // 2026-10-11 is a Sunday; Saturday and Sunday are off, so Friday's record carries over.
        val values = HabitAnalytics.entriesByDate(entries("2026-10-09"))
        assertEquals(1, HabitAnalytics.currentStreak(scheduled, values, today, LocalDate.parse("2026-09-01")))
    }

    @Test
    fun `completion rate counts planned days only`() {
        val weekdays = HabitSchedule.weekdays()
        val scheduled = Habit(id = 3, name = "Stretch", schedule = weekdays, startDate = "2026-10-01")
        val records = entries("2026-10-05", "2026-10-06", "2026-10-07") // Mon, Tue, Wed
        val rate = HabitAnalytics.completionRate(
            scheduled,
            HabitAnalytics.entriesByDate(records),
            today,
            LocalDate.parse("2026-10-01"),
            30,
        )
        // 1-11 October holds seven weekdays, three of them done.
        assertEquals(3f / 7f, rate, 0.001f)
    }

    @Test
    fun `quit habit counts clean days and remembers slips`() {
        val quit = Habit(id = 4, name = "No phone in bed", kind = HabitKind.QUIT, startDate = "2026-09-01")
        val records = listOf(HabitEntry(4, "2026-10-09", 1))
        val stats = HabitAnalytics.stats(quit, records, today)
        assertEquals(1, stats.slips)
        assertEquals(2, stats.cleanDays)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun `heatmap is a whole number of weeks ending with today's week`() {
        val days = HabitAnalytics.heatmap(habit, entries("2026-10-10"), today, weeks = 2)
        assertEquals(14, days.size)
        assertEquals(LocalDate.parse("2026-10-16"), days.last().date)
        assertEquals(HabitDayState.FUTURE, days.last().state)
        val todayCell = days.first { it.date == today }
        assertEquals(HabitDayState.TODAY, todayCell.state)
        assertEquals(HabitDayState.DONE, days.first { it.date == LocalDate.parse("2026-10-10") }.state)
        assertEquals(HabitDayState.MISSED, days.first { it.date == LocalDate.parse("2026-10-09") }.state)
    }

    @Test
    fun `days before tracking started are never judged`() {
        val late = Habit(id = 5, name = "Walk", startDate = "2026-10-10")
        val days = HabitAnalytics.heatmap(late, entries("2026-10-10"), today, weeks = 2)
        assertEquals(HabitDayState.OFF, days.first { it.date == LocalDate.parse("2026-10-09") }.state)
    }

    @Test
    fun `counted habit needs the daily target before a day counts`() {
        val counted = Habit(
            id = 6,
            name = "Water",
            tracking = HabitTracking.COUNT,
            dailyTarget = 8,
            startDate = "2026-10-01",
        )
        val records = listOf(HabitEntry(6, "2026-10-10", 5), HabitEntry(6, "2026-10-11", 8))
        val stats = HabitAnalytics.stats(counted, records, today)
        assertTrue(stats.todayDone)
        assertEquals(8, stats.todayValue)
        assertEquals(13, stats.totalValue)
    }

    @Test
    fun `a streak at risk asks the user not to miss twice`() {
        val records = entries("2026-10-08", "2026-10-09", "2026-10-10")
        val stats = HabitAnalytics.stats(habit, records, today)
        val keys = HabitAnalytics.insights(habit, stats).map { it.key }
        assertTrue(HabitInsightKey.NEVER_MISS_TWICE in keys)
        assertFalse(stats.todayDone)
    }

    @Test
    fun `a hard habit that is rarely completed is told to shrink the step`() {
        val hard = habit.copy(difficulty = HabitDifficulty.HARD)
        val stats = HabitAnalytics.stats(hard, entries("2026-10-03"), today)
        val keys = HabitAnalytics.insights(hard, stats).map { it.key }
        assertTrue(HabitInsightKey.SHRINK_STEP in keys)
    }

    @Test
    fun `a weekly target behind schedule says how many days are left`() {
        val weekly = Habit(
            id = 7,
            name = "Run",
            schedule = HabitSchedule(type = HabitScheduleType.TIMES_PER_WEEK, timesPerWeek = 4),
            startDate = "2026-10-01",
        )
        val stats = HabitAnalytics.stats(weekly, entries("2026-10-10"), today)
        val note = HabitAnalytics.insights(weekly, stats).first { it.key == HabitInsightKey.WEEKLY_TARGET_BEHIND }
        assertEquals(3, note.value)
        assertEquals(4, stats.targetThisWeek)
    }

    @Test
    fun `score rewards consistency, run length and direction`() {
        assertEquals(100, HabitAnalytics.score(1f, 21, 1f))
        assertEquals(0, HabitAnalytics.score(0f, 0, -1f))
        assertTrue(HabitAnalytics.score(0.6f, 10, 0.2f) > HabitAnalytics.score(0.6f, 10, -0.2f))
    }

    @Test
    fun `phase follows the automaticity ladder`() {
        assertEquals(HabitPhase.STARTING, HabitAnalytics.phase(3))
        assertEquals(HabitPhase.REPEATING, HabitAnalytics.phase(10))
        assertEquals(HabitPhase.SETTLING, HabitAnalytics.phase(30))
        assertEquals(HabitPhase.AUTOMATIC, HabitAnalytics.phase(66))
    }

    @Test
    fun `summary adds up today across every habit`() {
        val quit = Habit(id = 8, name = "No sugar", kind = HabitKind.QUIT, startDate = "2026-09-01")
        val records = entries("2026-10-11", "2026-10-10") + HabitEntry(8, "2026-10-11", 1)
        val summary = HabitAnalytics.summary(listOf(habit, quit), records, today)
        assertEquals(2, summary.habitCount)
        assertEquals(2, summary.scheduledToday)
        assertEquals(1, summary.doneToday)
        assertEquals(1, summary.quitCount)
        assertEquals(1, summary.buildCount)
    }

    @Test
    fun `an unknown weekday pattern leaves the advice out`() {
        val stats = HabitAnalytics.stats(habit, entries("2026-10-10", "2026-10-11"), today)
        assertNull(stats.weakestWeekday)
    }

    @Test
    fun `habits survive a serialization round trip`() {
        val original = habit.copy(
            schedule = HabitSchedule(type = HabitScheduleType.SPECIFIC_DAYS, weekdays = setOf(6, 7)),
            cue = "after brushing my teeth",
            themeId = "GARDEN",
        )
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<Habit>(json.encodeToString(Habit.serializer(), original))
        assertEquals(original, restored)
    }
}
