package com.marbledo.feature.tasks

import android.icu.util.Calendar
import android.icu.util.PersianCalendar
import android.icu.util.TimeZone as IcuTimeZone
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.util.RecurrenceCalculator
import java.time.Instant
import java.time.ZoneId

object AndroidRecurrenceCalculator {
    fun nextAfter(previousMillis: Long, rule: RecurrenceRule, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (rule.calendar == RepeatCalendar.GREGORIAN) {
            return RecurrenceCalculator.nextAfter(previousMillis, rule, zone)
        }
        require(rule.frequency == RepeatFrequency.MONTHLY || rule.frequency == RepeatFrequency.YEARLY) {
            "Persian-calendar rules support monthly and yearly intervals"
        }
        val local = Instant.ofEpochMilli(previousMillis).atZone(zone)
        val calendar = PersianCalendar(IcuTimeZone.getTimeZone(zone.id)).apply {
            timeInMillis = previousMillis
        }
        val targetDay = rule.monthDay ?: calendar.get(Calendar.DAY_OF_MONTH)
        if (rule.frequency == RepeatFrequency.YEARLY) {
            calendar.add(Calendar.YEAR, rule.interval)
        } else {
            calendar.add(Calendar.MONTH, rule.interval)
        }
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(lastDay))
        calendar.set(Calendar.HOUR_OF_DAY, local.hour)
        calendar.set(Calendar.MINUTE, local.minute)
        calendar.set(Calendar.SECOND, local.second)
        calendar.set(Calendar.MILLISECOND, local.nano / 1_000_000)
        return calendar.timeInMillis
    }
}
