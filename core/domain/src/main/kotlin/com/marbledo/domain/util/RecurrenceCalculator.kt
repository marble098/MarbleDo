package com.marbledo.domain.util

import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Date-only recurrence math; the caller supplies the device zone at the moment it schedules. */
object RecurrenceCalculator {
    fun nextAfter(
        previousEpochMillis: Long,
        rule: RecurrenceRule,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(rule.calendar == RepeatCalendar.GREGORIAN) {
            "Persian-calendar recurrence is calculated by the Android ICU adapter"
        }
        val previous = Instant.ofEpochMilli(previousEpochMillis).atZone(zoneId)
        val next = when (rule.frequency) {
            RepeatFrequency.NONE -> previous
            RepeatFrequency.DAILY, RepeatFrequency.CUSTOM -> previous.plusDays(rule.interval.toLong())
            RepeatFrequency.WEEKLY -> nextWeekly(previous, rule, zoneId)
            RepeatFrequency.MONTHLY -> nextMonthly(previous, rule)
            RepeatFrequency.YEARLY -> previous.plusYears(rule.interval.toLong())
        }
        return next.toInstant().toEpochMilli()
    }

    private fun nextMonthly(previous: ZonedDateTime, rule: RecurrenceRule): ZonedDateTime {
        val targetMonth = YearMonth.from(previous).plusMonths(rule.interval.toLong())
        val requestedDay = rule.monthDay ?: previous.dayOfMonth
        val date = targetMonth.atDay(requestedDay.coerceAtMost(targetMonth.lengthOfMonth()))
        return ZonedDateTime.of(date, previous.toLocalTime(), previous.zone)
    }

    private fun nextWeekly(previous: ZonedDateTime, rule: RecurrenceRule, zoneId: ZoneId): ZonedDateTime {
        val selectedDays = rule.daysOfWeek.ifEmpty { setOf(previous.dayOfWeek.value) }
        val start = previous.toLocalDate().plusDays(1)
        val anchorWeek = previous.toLocalDate().with(java.time.DayOfWeek.MONDAY)
        val maxDays = rule.interval * 7 + 14
        for (offset in 0..maxDays) {
            val candidate = start.plusDays(offset.toLong())
            val weekDistance = java.time.temporal.ChronoUnit.WEEKS.between(
                anchorWeek,
                candidate.with(java.time.DayOfWeek.MONDAY),
            )
            if (weekDistance >= 0 && weekDistance % rule.interval == 0L && candidate.dayOfWeek.value in selectedDays) {
                return ZonedDateTime.of(candidate, previous.toLocalTime(), zoneId)
            }
        }
        error("Could not calculate the next weekly recurrence")
    }
}
