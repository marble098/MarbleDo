package com.marbledo.feature.tasks

import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
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

        val jalaliMatch = Regex("""(?<!\d)(\d{1,2})\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)""")
            .find(input)
        if (jalaliMatch != null) {
            val day = jalaliMatch.groupValues[1].toIntOrNull()
            val month = persianMonths.indexOf(jalaliMatch.groupValues[2])
            if (day != null && month >= 0) {
                val calendar = persianCalendar(IcuTimeZone.getTimeZone(zone.id))
                val currentPersianYear = calendar.apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
                val candidate = (currentPersianYear..(currentPersianYear + 8))
                    .asSequence()
                    .mapNotNull { year -> persianDateMillis(year, month, day, zone) }
                    .firstOrNull { millis -> !Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().isBefore(now.toLocalDate()) }
                if (candidate != null) {
                    date = Instant.ofEpochMilli(candidate).atZone(zone).toLocalDate()
                    recognizedDate = true
                    matchedDate = jalaliMatch.value
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
                val weekdayPattern = Regex("""(?i)(شنبه|یک\s*شنبه|دوشنبه|سه\s*شنبه|چهارشنبه|پنج\s*شنبه|جمعه|saturday|sunday|monday|tuesday|wednesday|thursday|friday)(?:\s+(آینده|بعدی|next))?""")
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

        val time = parseTime(input)
        var title = input
        matchedDate?.let { title = title.replace(it, " ", ignoreCase = true) }
        time?.matchedText?.let { title = title.replace(it, " ", ignoreCase = true) }
        title = title
            .replace(Regex("(?i)\\b(?:ساعت|at|the)\\b"), " ")
            .replace(Regex("(?i)\\b(?:آینده|بعدی|next)\\b"), " ")
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

    private fun parseTime(input: String): ParsedTime? {
        val patterns = listOf(
            Regex("""(?i)(?:ساعت\s*|at\s*)(\d{1,2})(?::(\d{2}))?\s*(صبح|عصر|بعدازظهر|شب|am|pm)?"""),
            Regex("""(?i)(?<!\d)(\d{1,2}):(\d{2})\s*(صبح|عصر|بعدازظهر|شب|am|pm)?"""),
            Regex("""(?i)(?<!\d)(\d{1,2})\s*(صبح|عصر|بعدازظهر|شب|am|pm)"""),
        )
        val match = patterns.firstNotNullOfOrNull { it.find(input) } ?: return null
        val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        val suffix = match.groupValues.lastOrNull().orEmpty().lowercase()
        var hour = hourRaw
        when (suffix) {
            "عصر", "بعدازظهر", "شب", "pm" -> if (hour in 1..11) hour += 12
            "صبح", "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23) return null
        return ParsedTime(LocalTime.of(hour, minute), match.value)
    }

    private fun persianDateMillis(year: Int, month: Int, day: Int, zone: ZoneId): Long? = runCatching {
        persianCalendar(IcuTimeZone.getTimeZone(zone.id)).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()
}
