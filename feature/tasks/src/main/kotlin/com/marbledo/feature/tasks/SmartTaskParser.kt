package com.marbledo.feature.tasks

import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Offline Persian/English quick-add parser. Unrecognized text remains the task title. */
object SmartTaskParser {
    private val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    private val weekdays = mapOf(
        "شنبه" to DayOfWeek.SATURDAY,
        "یکشنبه" to DayOfWeek.SUNDAY,
        "یک شنبه" to DayOfWeek.SUNDAY,
        "دوشنبه" to DayOfWeek.MONDAY,
        "سه شنبه" to DayOfWeek.TUESDAY,
        "چهارشنبه" to DayOfWeek.WEDNESDAY,
        "پنجشنبه" to DayOfWeek.THURSDAY,
        "پنج شنبه" to DayOfWeek.THURSDAY,
        "جمعه" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
    )
    private val persianMonthPattern = Regex(
        """(?<!\d)(?:(\d{4})\s+)?(\d{1,2})\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)(?:\s+(\d{4}))?""",
    )
    private val numericDatePattern = Regex("""(?<!\d)(\d{4})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{1,2})(?!\d)""")
    private val persianCalendarLocale = ULocale("fa_IR@calendar=persian")
    private val gregorianCalendarLocale = ULocale("en_US@calendar=gregorian")

    data class Parsed(val task: Task, val recognizedDate: Boolean, val recognizedTime: Boolean)

    fun parse(raw: String, nowMillis: Long = System.currentTimeMillis()): Parsed? {
        val input = TextNormalizer.digitsToLatin(raw)
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .trim()
        if (input.isBlank()) return null

        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var date = now.toLocalDate()
        var recognizedDate = false
        var matchedDate: String? = null

        numericDatePattern.find(input)?.let { match ->
            val year = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null) {
                // Iranian-era years are unambiguously Persian in quick-add input; other years use ISO/Gregorian.
                val calendar = if (year in 1200..1599) DateCalendar.PERSIAN else DateCalendar.GREGORIAN
                val epoch = calendarDateMillis(year, month, day, calendar, zone)
                if (epoch != null) {
                    date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                    recognizedDate = true
                    matchedDate = match.value
                }
            }
        }

        if (!recognizedDate) {
            val match = persianMonthPattern.find(input)
            if (match != null) {
                val day = match.groupValues[2].toIntOrNull()
                val month = persianMonths.indexOf(match.groupValues[3])
                val explicitYear = match.groupValues[1].ifBlank { match.groupValues[4] }.toIntOrNull()
                if (day != null && month >= 0) {
                    val epoch = if (explicitYear != null) {
                        calendarDateMillis(explicitYear, month + 1, day, DateCalendar.PERSIAN, zone)
                    } else {
                        val calendar = persianCalendar(IcuTimeZone.getTimeZone(zone.id))
                        val currentPersianYear = calendar.apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
                        (currentPersianYear..(currentPersianYear + 8))
                            .asSequence()
                            .mapNotNull { year -> calendarDateMillis(year, month + 1, day, DateCalendar.PERSIAN, zone) }
                            .firstOrNull { millis ->
                                !Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().isBefore(now.toLocalDate())
                            }
                    }
                    if (epoch != null) {
                        date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                        recognizedDate = true
                        matchedDate = match.value
                    }
                }
            }
        }

        if (!recognizedDate) {
            val relative = Regex("""(?i)(پس\s*فردا|روز\s*بعد\s*از\s*فردا|فردا|امروز|tomorrow|today|day after tomorrow)""")
                .find(input)
            if (relative != null) {
                val key = relative.value.lowercase().replace(" ", "")
                date = when (key) {
                    "امروز", "today" -> now.toLocalDate()
                    "فردا", "tomorrow" -> now.toLocalDate().plusDays(1)
                    else -> now.toLocalDate().plusDays(2)
                }
                recognizedDate = true
                matchedDate = relative.value
            } else {
                val relativeDays = Regex(
                    """(?i)(?:(\d+|یک|دو|سه|چهار|پنج|شش|هفت)\s*)?(?:روز|day)s?\s*(?:بعد|دیگر|دیگه|later)|(?:in\s+(\d+)\s+days?)""",
                ).find(input)
                val relativeWeeks = Regex(
                    """(?i)(?:(\d+|یک|دو|سه|چهار)\s*)?(?:هفته|week)s?\s*(?:بعد|دیگر|دیگه|آینده|later)|(?:in\s+(\d+)\s+weeks?)|(?:next\s+week)""",
                ).find(input)
                val relativeMonths = Regex("""(?i)(ماه\s*(?:بعد|آینده)|next\s+month|in\s+one\s+month)""").find(input)
                when {
                    relativeDays != null -> {
                        val amount = (relativeDays.groupValues[1].ifBlank { relativeDays.groupValues[2] })
                            .takeIf(String::isNotBlank)?.let(::spokenInteger) ?: 1
                        date = now.toLocalDate().plusDays(amount.toLong().coerceAtLeast(1))
                        matchedDate = relativeDays.value
                        recognizedDate = true
                    }
                    relativeWeeks != null -> {
                        val amount = (relativeWeeks.groupValues[1].ifBlank { relativeWeeks.groupValues[2] })
                            .takeIf(String::isNotBlank)?.let(::spokenInteger) ?: 1
                        date = now.toLocalDate().plusWeeks(amount.toLong().coerceAtLeast(1))
                        matchedDate = relativeWeeks.value
                        recognizedDate = true
                    }
                    relativeMonths != null -> {
                        date = now.toLocalDate().plusMonths(1)
                        matchedDate = relativeMonths.value
                        recognizedDate = true
                    }
                }
                if (!recognizedDate) {
                    val weekdayPattern = Regex(
                        """(?i)(شنبه|یک\s*شنبه|دوشنبه|سه\s*شنبه|چهارشنبه|پنج\s*شنبه|جمعه|saturday|sunday|monday|tuesday|wednesday|thursday|friday)(?:\s+(آینده|بعدی|next))?""",
                    )
                    val weekdayMatch = weekdayPattern.find(input)
                    if (weekdayMatch != null) {
                        val dayKey = weekdayMatch.groupValues[1].lowercase().replace(Regex("\\s+"), "")
                        val target = weekdays.entries.firstOrNull { it.key.replace(" ", "") == dayKey }?.value
                        if (target != null) {
                            var days = (target.value - now.dayOfWeek.value + 7) % 7
                            val futureWord = weekdayMatch.groupValues[2].isNotBlank()
                            if (days == 0 || futureWord) days += 7
                            date = now.toLocalDate().plusDays(days.toLong())
                            recognizedDate = true
                            matchedDate = weekdayMatch.value
                        }
                    }
                }
            }
        }

        val time = parseTime(input)
        var title = input
        matchedDate?.let { title = title.replace(it, " ", ignoreCase = true) }
        time?.matchedText?.let { title = title.replace(it, " ", ignoreCase = true) }
        title = title
            .replace(Regex("(?i)\\b(?:ساعت|at)\\b"), " ")
            .replace(Regex("(?i)\\b(?:آینده|بعدی|next|later)\\b"), " ")
            .replace(Regex("[،,:;|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '،', '.', ':')
        if (title.isBlank()) return null

        val localTime = time?.time ?: LocalTime.of(9, 0)
        if (!recognizedDate && time != null && date == now.toLocalDate() && localTime <= now.toLocalTime()) {
            date = date.plusDays(1)
        }
        val dueMillis = if (recognizedDate || time != null) {
            ZonedDateTime.of(date, localTime, zone).toInstant().toEpochMilli()
        } else {
            null
        }
        return Parsed(
            task = Task(title = title, dueAtEpochMillis = dueMillis, isAllDay = recognizedDate && time == null),
            recognizedDate = recognizedDate,
            recognizedTime = time != null,
        )
    }

    private data class ParsedTime(val time: LocalTime, val matchedText: String)
    private enum class DateCalendar { PERSIAN, GREGORIAN }

    private fun parseTime(input: String): ParsedTime? {
        val spokenTime = Regex(
            """(?i)(?:ساعت\s*|at\s*)(\d{1,2})(?:\s*(?::|٫|،|و)\s*(\d{1,2})\s*(?:دقیقه)?)?\s*(صبح|عصر|بعدازظهر|شب|am|pm)?""",
        ).find(input)
        val clockTime = Regex("""(?i)(?<!\d)(\d{1,2}):(\d{2})\s*(صبح|عصر|بعدازظهر|شب|am|pm)?""").find(input)
        val suffixedTime = Regex("""(?i)(?<!\d)(\d{1,2})\s*(صبح|عصر|بعدازظهر|شب|am|pm)""").find(input)
        val match = spokenTime ?: clockTime ?: suffixedTime ?: return null
        val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
        val minuteText = if (match === suffixedTime) "" else match.groupValues.getOrNull(2).orEmpty()
        val minute = if (minuteText.isBlank()) 0 else minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val suffix = when {
            match === suffixedTime -> match.groupValues.getOrNull(2).orEmpty().lowercase()
            else -> match.groupValues.getOrNull(3).orEmpty().lowercase()
        }
        var hour = hourRaw
        when (suffix) {
            "عصر", "بعدازظهر", "شب", "pm" -> if (hour in 1..11) hour += 12
            "صبح", "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23) return null
        return ParsedTime(LocalTime.of(hour, minute), match.value)
    }

    private fun spokenInteger(value: String): Int = value.toIntOrNull() ?: when (value) {
        "یک" -> 1
        "دو" -> 2
        "سه" -> 3
        "چهار" -> 4
        "پنج" -> 5
        "شش" -> 6
        "هفت" -> 7
        else -> 1
    }

    private fun calendarDateMillis(year: Int, month: Int, day: Int, calendar: DateCalendar, zone: ZoneId): Long? = runCatching {
        val icuZone = IcuTimeZone.getTimeZone(zone.id)
        val instance = when (calendar) {
            DateCalendar.PERSIAN -> Calendar.getInstance(icuZone, persianCalendarLocale)
            DateCalendar.GREGORIAN -> Calendar.getInstance(icuZone, gregorianCalendarLocale)
        }
        instance.apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()
}
