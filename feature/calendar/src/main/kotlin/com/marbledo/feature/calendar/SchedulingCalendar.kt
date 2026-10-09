package com.marbledo.feature.calendar

import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Year, zero-based month and day of month in one calendar system. */
internal data class CalendarFields(val year: Int, val month: Int, val day: Int)

/**
 * ICU-backed conversions between Gregorian [LocalDate] values and the Persian, Gregorian and Islamic civil
 * calendars. Conversions happen at local noon, so a daylight-saving change can never move the selected day.
 */
internal object SchedulingCalendar {
    private val persianLocale = ULocale("fa_IR@calendar=persian")
    private val gregorianLocale = ULocale("en_US@calendar=gregorian")
    private val islamicLocale = ULocale("ar@calendar=islamic-civil")

    private fun calendarFor(mode: CalendarDisplayMode, zone: ZoneId): Calendar {
        val locale = when (mode) {
            CalendarDisplayMode.PERSIAN -> persianLocale
            CalendarDisplayMode.GREGORIAN -> gregorianLocale
            CalendarDisplayMode.ISLAMIC_CIVIL -> islamicLocale
        }
        return Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), locale)
    }

    fun fieldsOf(date: LocalDate, mode: CalendarDisplayMode, zone: ZoneId): CalendarFields {
        val calendar = calendarFor(mode, zone).apply { timeInMillis = noonMillis(date, zone) }
        return CalendarFields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    /** Returns null when the requested day does not exist in that calendar, for example day 31 of a 30-day month. */
    fun dateOf(fields: CalendarFields, mode: CalendarDisplayMode, zone: ZoneId): LocalDate? = runCatching {
        val calendar = calendarFor(mode, zone).apply {
            isLenient = false
            clear()
            set(fields.year, fields.month, fields.day, 12, 0, 0)
        }
        Instant.ofEpochMilli(calendar.timeInMillis).atZone(zone).toLocalDate()
    }.getOrNull()

    /** ICU day-of-week of the first day of the month, where Sunday is 1 and Saturday is 7. */
    fun firstDayOfWeek(year: Int, month: Int, mode: CalendarDisplayMode, zone: ZoneId): Int {
        val calendar = calendarFor(mode, zone).apply {
            clear()
            set(year, month, 1, 12, 0, 0)
        }
        return calendar.get(Calendar.DAY_OF_WEEK)
    }

    fun daysInMonth(year: Int, month: Int, mode: CalendarDisplayMode, zone: ZoneId): Int {
        val calendar = calendarFor(mode, zone).apply {
            clear()
            set(year, month, 1, 12, 0, 0)
        }
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Moves by whole months inside [mode] and returns the first day of the resulting month. */
    fun shiftMonths(date: LocalDate, months: Int, mode: CalendarDisplayMode, zone: ZoneId): LocalDate {
        val fields = fieldsOf(date, mode, zone)
        val total = fields.year * 12 + fields.month + months
        val target = CalendarFields(Math.floorDiv(total, 12), Math.floorMod(total, 12), 1)
        return dateOf(target, mode, zone) ?: date
    }

    private fun noonMillis(date: LocalDate, zone: ZoneId): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}

/**
 * Number of empty grid cells before day 1. ICU weekday numbers run Sunday = 1 through Saturday = 7.
 * A Saturday-first week puts Saturday in column 0; a Sunday-first week puts Sunday there.
 */
fun leadingBlankCells(icuDayOfWeek: Int, weekStartsSaturday: Boolean): Int {
    require(icuDayOfWeek in 1..7) { "ICU weekday must be between 1 and 7" }
    return if (weekStartsSaturday) icuDayOfWeek % 7 else icuDayOfWeek - 1
}

/** Persian weekday indices (0 = Saturday … 6 = Friday) in the order a grid header should show them. */
fun weekdayColumnOrder(weekStartsSaturday: Boolean): List<Int> =
    if (weekStartsSaturday) (0..6).toList() else listOf(1, 2, 3, 4, 5, 6, 0)

/** Month names in the order of the ICU month field, which is zero-based. */
fun calendarMonthNames(mode: CalendarDisplayMode, languageTag: String): List<String> = when (mode) {
    CalendarDisplayMode.PERSIAN -> PersianDateUtils.monthNames(languageTag)
    CalendarDisplayMode.GREGORIAN -> PersianDateUtils.gregorianMonthNames(languageTag)
    CalendarDisplayMode.ISLAMIC_CIVIL -> PersianDateUtils.islamicMonthNames(languageTag)
}

/** Latin 24-hour clock text such as `14:30`. Callers apply the numeral mode to the full sentence. */
fun formatClock(time: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

/** Example: `دوشنبه ۱۲ آبان ۱۴۰۵ · ۱۴:۳۰` in Persian, using the chosen calendar for the day, month and year. */
fun formatScheduleSummary(
    date: LocalDate,
    time: LocalTime?,
    calendarMode: CalendarDisplayMode,
    languageTag: String,
    numeralMode: NumeralMode,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val fields = SchedulingCalendar.fieldsOf(date, calendarMode, zone)
    val monthName = calendarMonthNames(calendarMode, languageTag).getOrElse(fields.month) { "" }
    val weekday = PersianDateUtils.weekdayName(saturdayBasedIndex(date.dayOfWeek), languageTag)
    val day = "$weekday ${fields.day} $monthName ${fields.year}"
    val text = if (time == null) day else "$day · ${formatClock(time)}"
    return TextNormalizer.formatDigits(text, numeralMode)
}

/** Saturday = 0 through Friday = 6, matching the Persian week used throughout the app. */
internal fun saturdayBasedIndex(dayOfWeek: DayOfWeek): Int = (dayOfWeek.value - 6 + 7) % 7

/** Day of the month in [mode] for the local day that contains [epochMillis]. Drives the status-bar date icon. */
fun dayOfMonthIn(epochMillis: Long, mode: CalendarDisplayMode, zone: ZoneId = ZoneId.systemDefault()): Int {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return SchedulingCalendar.fieldsOf(date, mode, zone).day
}
