package com.marbledo.feature.tasks

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskScheduleTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 9)

    private fun millis(date: LocalDate, time: LocalTime): Long =
        ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

    @Test
    fun `an empty schedule has no due moment`() {
        assertNull(TaskSchedule(null, null).toDueMillis(zone, today))
    }

    @Test
    fun `a date without a time is due at nine in the morning`() {
        val due = TaskSchedule(today, null).toDueMillis(zone, today)
        assertEquals(millis(today, LocalTime.of(9, 0)), due)
    }

    @Test
    fun `a time without a date uses the fallback day`() {
        val due = TaskSchedule(null, LocalTime.of(18, 30)).toDueMillis(zone, today)
        assertEquals(millis(today, LocalTime.of(18, 30)), due)
    }

    @Test
    fun `a time that is still ahead keeps today`() {
        val now = ZonedDateTime.of(today, LocalTime.of(8, 0), zone)
        assertEquals(today, defaultDateFor(LocalTime.of(18, 0), now))
    }

    @Test
    fun `a time that has passed moves to tomorrow`() {
        val now = ZonedDateTime.of(today, LocalTime.of(20, 0), zone)
        assertEquals(today.plusDays(1), defaultDateFor(LocalTime.of(18, 0), now))
    }

    @Test
    fun `no time means today`() {
        val now = ZonedDateTime.of(today, LocalTime.of(20, 0), zone)
        assertEquals(today, defaultDateFor(null, now))
    }

    @Test
    fun `scheduleOf reads an all-day task as a date without a time`() {
        val epoch = millis(today, LocalTime.of(9, 0))
        val schedule = scheduleOf(epoch, allDay = true, zone = zone)
        assertEquals(today, schedule.date)
        assertNull(schedule.time)
    }

    @Test
    fun `scheduleOf reads a timed task with its time`() {
        val epoch = millis(today, LocalTime.of(14, 30))
        val schedule = scheduleOf(epoch, allDay = false, zone = zone)
        assertEquals(LocalTime.of(14, 30), schedule.time)
    }

    @Test
    fun `a timed task is overdue once its moment has passed`() {
        val due = millis(today, LocalTime.of(10, 0))
        val after = millis(today, LocalTime.of(10, 1))
        assertTrue(taskIsOverdue(false, false, due, isAllDay = false, nowMillis = after, zone = zone))
        assertFalse(taskIsOverdue(false, false, due, isAllDay = false, nowMillis = due - 1, zone = zone))
    }

    @Test
    fun `an all-day task is overdue only after its date`() {
        val due = millis(today, LocalTime.of(9, 0))
        val sameDayEvening = millis(today, LocalTime.of(23, 0))
        val nextDay = millis(today.plusDays(1), LocalTime.of(0, 30))
        assertFalse(taskIsOverdue(false, false, due, isAllDay = true, nowMillis = sameDayEvening, zone = zone))
        assertTrue(taskIsOverdue(false, false, due, isAllDay = true, nowMillis = nextDay, zone = zone))
    }

    @Test
    fun `completed, archived and undated tasks are never overdue`() {
        val due = millis(today, LocalTime.of(10, 0))
        val later = due + 3_600_000L
        assertFalse(taskIsOverdue(true, false, due, isAllDay = false, nowMillis = later, zone = zone))
        assertFalse(taskIsOverdue(false, true, due, isAllDay = false, nowMillis = later, zone = zone))
        assertFalse(taskIsOverdue(false, false, null, isAllDay = false, nowMillis = later, zone = zone))
    }
}
