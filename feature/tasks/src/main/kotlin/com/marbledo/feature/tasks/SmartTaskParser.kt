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

/**
 * Offline Persian/English quick-add parser. Unrecognized text remains the task title.
 *
 * It understands three families of schedule, in this order of precedence:
 *
 *  1. a written date — `1405/08/25`, `25 آبان`, `2026-11-09`;
 *  2. a relative day — `امروز`, `فردا`, `پس‌فردا`, `فردا همین زمان`, `آخر هفته`, a weekday
 *     (`شنبه`, `دوشنبهٔ آینده`), or a spoken distance (`ده روز بعد`, `دو هفتهٔ دیگر`,
 *     `سه ماه بعد`, `نیم ساعت بعد`, `in 10 days`, `next month`);
 *  3. a clock — `ساعت ۱۴ و ۳۰ دقیقه`, `۳ و نیم بعد از ظهر`, `14:30`, `۸ صبح`, `ظهر`, `midnight`.
 *
 * A spoken distance that is measured in minutes or hours also decides the day when it crosses
 * midnight, and a distance in days or more keeps the current clock, because "ده روز بعد" means ten
 * days from now at the same time. That is why a task written as `ده روز بعد ساعت ۹ صبح` is both
 * dated and timed while `ده روز بعد` alone is dated at the current time of day.
 */
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

    /**
     * Spoken numbers, Persian and English. The parser sums a short ladder of these, so `بیست و یک`,
     * `twenty one`, `صد` and `یک` all resolve without a grammar library, and `نیم`/`ربع` cover the
     * half-hour and quarter-hour phrases that come up when people speak a time out loud.
     */
    private val spokenNumbers = mapOf(
        "صفر" to 0.0, "یک" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0, "پنج" to 5.0,
        "شش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0, "ده" to 10.0,
        "یازده" to 11.0, "دوازده" to 12.0, "سیزده" to 13.0, "چهارده" to 14.0,
        "پانزده" to 15.0, "شانزده" to 16.0, "هفده" to 17.0, "هجده" to 18.0, "نوزده" to 19.0,
        "بیست" to 20.0, "سی" to 30.0, "چهل" to 40.0, "پنجاه" to 50.0, "شصت" to 60.0,
        "هفتاد" to 70.0, "هشتاد" to 80.0, "نود" to 90.0, "صد" to 100.0,
        "نیم" to 0.5, "ربع" to 0.25,
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0,
        "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0,
        "sixteen" to 16.0, "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0,
        "sixty" to 60.0, "half" to 0.5, "quarter" to 0.25, "a" to 1.0, "an" to 1.0,
    )

    private enum class RelativeUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

    private val relativeUnits = mapOf(
        "دقیقه" to RelativeUnit.MINUTE,
        "ساعت" to RelativeUnit.HOUR,
        "روز" to RelativeUnit.DAY,
        "هفته" to RelativeUnit.WEEK,
        "ماه" to RelativeUnit.MONTH,
        "سال" to RelativeUnit.YEAR,
        "minute" to RelativeUnit.MINUTE,
        "minutes" to RelativeUnit.MINUTE,
        "hour" to RelativeUnit.HOUR,
        "hours" to RelativeUnit.HOUR,
        "day" to RelativeUnit.DAY,
        "days" to RelativeUnit.DAY,
        "week" to RelativeUnit.WEEK,
        "weeks" to RelativeUnit.WEEK,
        "month" to RelativeUnit.MONTH,
        "months" to RelativeUnit.MONTH,
        "year" to RelativeUnit.YEAR,
        "years" to RelativeUnit.YEAR,
    )

    // Longer alternatives come first: with `day|days` the prefix form stopped at "day" and left a
    // stray "s" behind in the title.
    private val unitPattern = "(دقیقه|ساعت|روز|هفته|ماه|سال|minutes|minute|hours|hour|days|day|weeks|week|months|month|years|year)"
    private val qualifierPattern = "(?:بعد|دیگر|دیگه|بعدش|later|from\\s+now)"
    private val numberToken = "([0-9]{1,3}|[\\p{L}\\u200c]+(?:\\s+و\\s+[\\p{L}\\u200c]+){0,3})"

    private val suffixDuration = Regex("(?i)$unitPattern\\s*(?:[ی\\u200c]\\s*)?$qualifierPattern")
    private val prefixDuration = Regex("(?i)(?:بعد\\s+از|پس\\s+از|in|within)\\s+$numberToken\\s*(?:[ی\\u200c]\\s*)?$unitPattern")
    private val trailingNumber = Regex("(?i)$numberToken\\s*(?:و\\s*)?$")
    private val sameTimeMarker = Regex("(?i)(?:همین\\s*(?:زمان|ساعت|ساعتِ)|same\\s+time)")
    private val dayKeyword = Regex(
        "(?i)(پس\\s*فردا|روز\\s*بعد\\s*از\\s*فردا|فردا|امروز|tomorrow|today|day after tomorrow)",
    )
    private val weekendKeyword = Regex("(?i)(آخر\\s*هفته|تعطیلات\\s*آخر\\s*هفته|this\\s+weekend|next\\s+weekend)")

    private val persianMonthPattern = Regex(
        """(?<!\d)(?:(\d{4})\s+)?(\d{1,2})\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)(?:\s+(\d{4}))?""",
    )
    private val numericDatePattern = Regex("""(?<!\d)(\d{4})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{1,2})(?!\d)""")
    private val persianCalendarLocale = ULocale("fa_IR@calendar=persian")
    private val gregorianCalendarLocale = ULocale("en_US@calendar=gregorian")

    data class Parsed(val task: Task, val recognizedDate: Boolean, val recognizedTime: Boolean)

    /**
     * Date and time found in free text. [title] may be blank when the text only names a schedule, such as
     * "فردا" or "18:30", which the schedule picker and quick add use directly.
     */
    data class ScheduleParse(
        val title: String,
        val dueAtEpochMillis: Long?,
        val isAllDay: Boolean,
        val recognizedDate: Boolean,
        val recognizedTime: Boolean,
    )

    private data class Analysis(
        val title: String,
        val dueAtEpochMillis: Long?,
        val isAllDay: Boolean,
        val recognizedDate: Boolean,
        val recognizedTime: Boolean,
    )

    /** A spoken distance such as `ده روز بعد` or `in 2 hours`, with the text it consumed. */
    private data class RelativeMatch(val amount: Double, val unit: RelativeUnit, val matchedText: String)

    /** Builds a task from free text. Returns null when the text is blank or contains only a schedule. */
    fun parse(raw: String, nowMillis: Long = System.currentTimeMillis()): Parsed? {
        val analysis = analyze(raw, nowMillis) ?: return null
        if (analysis.title.isBlank()) return null
        return Parsed(
            task = Task(
                title = analysis.title,
                dueAtEpochMillis = analysis.dueAtEpochMillis,
                isAllDay = analysis.isAllDay,
            ),
            recognizedDate = analysis.recognizedDate,
            recognizedTime = analysis.recognizedTime,
        )
    }

    /** Schedule and title from free text, accepting text that has no title. Returns null only for blank input. */
    fun parseSchedule(raw: String, nowMillis: Long = System.currentTimeMillis()): ScheduleParse? =
        analyze(raw, nowMillis)?.let { analysis ->
            ScheduleParse(
                title = analysis.title,
                dueAtEpochMillis = analysis.dueAtEpochMillis,
                isAllDay = analysis.isAllDay,
                recognizedDate = analysis.recognizedDate,
                recognizedTime = analysis.recognizedTime,
            )
        }

    private fun analyze(raw: String, nowMillis: Long): Analysis? {
        val input = TextNormalizer.digitsToLatin(raw)
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('\u0654', ' ')
            .trim()
        if (input.isBlank()) return null

        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var date = now.toLocalDate()
        // A schedule that came with its own clock (a spoken distance or "همین زمان") wins over the
        // 09:00 default a bare date would use.
        var relativeTime: LocalTime? = null
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
            val relative = dayKeyword.find(input)
            if (relative != null) {
                val key = relative.groupValues[1].lowercase().replace(" ", "")
                date = when (key) {
                    "امروز", "today" -> now.toLocalDate()
                    "فردا", "tomorrow" -> now.toLocalDate().plusDays(1)
                    else -> now.toLocalDate().plusDays(2)
                }
                recognizedDate = true
                // "فردا همین زمان" / "tomorrow same time" keeps the current clock instead of 09:00.
                val after = input.substring(relative.range.last + 1)
                val marker = sameTimeMarker.find(after)?.takeIf { it.range.first <= 1 }
                if (marker != null) relativeTime = now.toLocalTime().withSecond(0).withNano(0)
                matchedDate = relative.value + (marker?.value?.let { " $it" } ?: "")
            }
        }

        if (!recognizedDate) {
            val weekend = weekendKeyword.find(input)
            if (weekend != null) {
                // The Persian weekend is Thursday/Friday; the last day of it is the Friday ahead.
                val daysAhead = (DayOfWeek.FRIDAY.value - now.dayOfWeek.value + 7) % 7
                date = now.toLocalDate().plusDays((if (daysAhead == 0) 7 else daysAhead).toLong())
                recognizedDate = true
                matchedDate = weekend.value
            }
        }

        if (!recognizedDate) {
            val relative = findRelative(input)
            if (relative != null) {
                val target = when (relative.unit) {
                    RelativeUnit.MINUTE -> now.plusMinutes(relative.amount.toLong())
                    RelativeUnit.HOUR -> now.plusMinutes((relative.amount * 60).toLong())
                    RelativeUnit.DAY -> now.plusDays(relative.amount.toLong())
                    RelativeUnit.WEEK -> now.plusDays((relative.amount * 7).toLong())
                    RelativeUnit.MONTH -> now.plusMonths(relative.amount.toLong())
                    RelativeUnit.YEAR -> now.plusYears(relative.amount.toLong())
                }
                date = target.toLocalDate()
                relativeTime = target.toLocalTime().withSecond(0).withNano(0)
                recognizedDate = true
                matchedDate = relative.matchedText
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

        val time = parseTime(input)
        var title = input
        matchedDate?.let { title = title.replace(it, " ", ignoreCase = true) }
        time?.matchedText?.let { title = title.replace(it, " ", ignoreCase = true) }
        title = title
            .replace(Regex("(?i)\\b(?:ساعت|at)\\b"), " ")
            .replace(sameTimeMarker, " ")
            .replace(Regex("(?i)\\b(?:آینده|بعدی|next|later)\\b"), " ")
            .replace(Regex("[،,:;|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '،', '.', ':')
        val localTime = time?.time ?: relativeTime ?: LocalTime.of(9, 0)
        if (!recognizedDate && time != null && date == now.toLocalDate() && localTime <= now.toLocalTime()) {
            date = date.plusDays(1)
        }
        val dueMillis = if (recognizedDate || time != null) {
            ZonedDateTime.of(date, localTime, zone).toInstant().toEpochMilli()
        } else {
            null
        }
        return Analysis(
            title = title,
            dueAtEpochMillis = dueMillis,
            isAllDay = recognizedDate && time == null && relativeTime == null,
            recognizedDate = recognizedDate,
            recognizedTime = time != null || relativeTime != null,
        )
    }

    /**
     * Finds a spoken distance in either word order: `ده روز بعد` / `two weeks later` (number before
     * the unit) and `بعد از ده روز` / `in 10 days` (number after it). A bare `هفته بعد` or
     * `next month` counts as one unit, which is what those phrases mean without a number.
     */
    private fun findRelative(input: String): RelativeMatch? {
        suffixDuration.findAll(input).forEach { match ->
            val unit = relativeUnits[match.groupValues[1].lowercase()] ?: return@forEach
            val prefix = input.substring(0, match.range.first)
            val number = trailingNumber.find(prefix)
            val amount = number?.groupValues?.get(1)?.let(::spokenAmount)
            // A bare unit ("سال بعد", "next month") means one of them; only a number that really
            // parses extends the matched text backwards, so "بازبینی سال بعد" loses "سال بعد" and
            // keeps "بازبینی".
            val start = if (number != null && amount != null) number.range.first else match.range.first
            val matched = input.substring(start, match.range.last + 1).trim()
            return RelativeMatch(amount ?: 1.0, unit, matched)
        }
        prefixDuration.findAll(input).forEach { match ->
            val amount = spokenAmount(match.groupValues[1]) ?: return@forEach
            val unit = relativeUnits[match.groupValues[2].lowercase()] ?: return@forEach
            return RelativeMatch(amount, unit, match.value.trim())
        }
        return null
    }

    /**
     * A spoken number: digits, or a short ladder of number words summed from the end, so
     * `بیست و یک` is 21 and `فردا نیم` still finds the half.
     */
    private fun spokenAmount(text: String): Double? {
        text.toDoubleOrNull()?.let { return it }
        val tokens = text.lowercase()
            .replace('\u200c', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it != "و" && it != "and" }
        if (tokens.isEmpty()) return null
        var total = 0.0
        var seen = false
        for (index in tokens.indices.reversed()) {
            val value = tokens[index].toDoubleOrNull() ?: spokenNumbers[tokens[index]] ?: break
            total += value
            seen = true
        }
        return if (seen) total else null
    }

    private data class ParsedTime(val time: LocalTime, val matchedText: String)
    private enum class DateCalendar { PERSIAN, GREGORIAN }

    private fun parseTime(input: String): ParsedTime? {
        val spokenTime = Regex(
            """(?i)(?:ساعت\s*|at\s*)(\d{1,2}|[آ-ی]+)(?:\s*(?::|٫|،|و)\s*(\d{1,2}|نیم|ربع)\s*(?:دقیقه|دقیقه‌ای)?)?\s*(صبح|عصر|بعد\s*از\s*ظهر|بعدازظهر|شب|am|pm)?""",
        ).find(input)
        val clockTime = Regex("""(?i)(?<!\d)(\d{1,2}):(\d{2})\s*(صبح|عصر|بعد\s*از\s*ظهر|بعدازظهر|شب|am|pm)?""").find(input)
        val suffixedTime = Regex("""(?i)(?<!\d)(\d{1,2})\s*(صبح|عصر|بعد\s*از\s*ظهر|بعدازظهر|شب|am|pm)""").find(input)
        val match = spokenTime ?: clockTime ?: suffixedTime ?: return namedTime(input)
        val hourRaw = match.groupValues[1].toIntOrNull()
            ?: spokenAmount(match.groupValues[1])?.toInt()?.takeIf { it in 0..23 }
            ?: return namedTime(input)
        val minuteText = if (match === suffixedTime) "" else match.groupValues.getOrNull(2).orEmpty()
        val minute = when {
            minuteText.isBlank() -> 0
            minuteText == "نیم" -> 30
            minuteText == "ربع" -> 15
            else -> minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        }
        val suffix = when {
            match === suffixedTime -> match.groupValues.getOrNull(2).orEmpty().lowercase()
            else -> match.groupValues.getOrNull(3).orEmpty().lowercase()
        }
        var hour = hourRaw
        when (suffix) {
            "عصر", "بعدازظهر", "بعد از ظهر", "شب", "pm" -> if (hour in 1..11) hour += 12
            "صبح", "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23) return null
        return ParsedTime(LocalTime.of(hour, minute), match.value)
    }

    /**
     * A time written as a word instead of digits. "بعد از ظهر" is read as one phrase, because matching
     * the bare "ظهر" would have left "بعد از" in the title; it means the afternoon rather than noon.
     */
    private fun namedTime(input: String): ParsedTime? {
        val named = Regex(
            "(?i)(?:ساعت\\s*|at\\s*)?(بعد\\s*از\\s*ظهر|بعدازظهر|afternoon|ظهر|noon|نیمه\\s*شب|نصف\\s*شب|midnight)",
        ).find(input) ?: return null
        val hour = when (named.groupValues[1].lowercase().replace(" ", "")) {
            "بعدازظهر", "afternoon" -> 15
            "ظهر", "noon" -> 12
            else -> 0
        }
        return ParsedTime(LocalTime.of(hour, 0), named.value)
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
