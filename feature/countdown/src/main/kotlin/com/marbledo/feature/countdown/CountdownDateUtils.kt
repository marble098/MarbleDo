package com.marbledo.feature.countdown

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.tasks.SmartTaskParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Calendar-aware formatting and validation for countdown dates, using Android's public ICU APIs. */
object CountdownDateUtils {
    private val persianLocale = ULocale("fa_IR@calendar=persian")
    private val gregorianLocale = ULocale("en_US@calendar=gregorian")
    private val islamicLocale = ULocale("ar@calendar=islamic-civil")
    private val numericDate = Regex("""^\s*(\d{1,4})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*$""")
    private val timeWithMinutes = Regex("""^\s*(\d{1,2})\s*(?::|٫|،|\s+و\s+)(\d{1,2})\s*(?:دقیقه)?\s*$""")
    private val hourOnly = Regex("""^\s*(\d{1,2})\s*$""")

    fun dateInput(epochMillis: Long, mode: CalendarDisplayMode, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val fields = calendar(mode, zoneId).apply { timeInMillis = epochMillis }
        return String.format(Locale.ROOT, "%04d/%02d/%02d", fields.get(Calendar.YEAR), fields.get(Calendar.MONTH) + 1, fields.get(Calendar.DAY_OF_MONTH))
    }

    fun timeInput(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    fun parseDateInput(value: String, mode: CalendarDisplayMode, nowMillis: Long = System.currentTimeMillis()): LocalDate? {
        val input = TextNormalizer.digitsToLatin(value.trim()).replace('ي', 'ی').replace('ك', 'ک')
        if (input.isBlank()) return null
        val zone = ZoneId.systemDefault()
        val numeric = numericDate.matchEntire(input)
        if (numeric != null) {
            val year = numeric.groupValues[1].toIntOrNull() ?: return null
            val month = numeric.groupValues[2].toIntOrNull() ?: return null
            val day = numeric.groupValues[3].toIntOrNull() ?: return null
            return fieldsToDate(year, month, day, mode, zone)
        }
        val spoken = SmartTaskParser.parse("$input countdown", nowMillis)
        return spoken?.takeIf { it.recognizedDate }?.task?.dueAtEpochMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }

    fun parseTimeInput(value: String): LocalTime? {
        val input = TextNormalizer.digitsToLatin(value.trim())
        val time = timeWithMinutes.matchEntire(input)
        val hour: Int
        val minute: Int
        if (time != null) {
            hour = time.groupValues[1].toIntOrNull() ?: return null
            minute = time.groupValues[2].toIntOrNull() ?: return null
        } else {
            val onlyHour = hourOnly.matchEntire(input) ?: return null
            hour = onlyHour.groupValues[1].toIntOrNull() ?: return null
            minute = 0
        }
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    fun parseDateTime(
        dateText: String,
        timeText: String,
        mode: CalendarDisplayMode,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val date = parseDateInput(dateText, mode, nowMillis) ?: return null
        val time = parseTimeInput(timeText) ?: return null
        return runCatching {
            date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun format(
        epochMillis: Long,
        mode: CalendarDisplayMode,
        locale: Locale = Locale.getDefault(),
        numeralMode: NumeralMode = NumeralMode.PERSIAN,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val fields = calendar(mode, zoneId).apply { timeInMillis = epochMillis }
        val year = fields.get(Calendar.YEAR)
        val month = fields.get(Calendar.MONTH)
        val day = fields.get(Calendar.DAY_OF_MONTH)
        val monthName = when (mode) {
            CalendarDisplayMode.PERSIAN -> monthName(month, locale.language == "fa", persianMonthsFa, persianMonthsEn)
            CalendarDisplayMode.GREGORIAN -> monthName(month, locale.language == "fa", gregorianMonthsFa, gregorianMonthsEn)
            CalendarDisplayMode.ISLAMIC_CIVIL -> monthName(month, locale.language == "fa", islamicMonthsFa, islamicMonthsEn)
        }
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT, if (locale.language == "fa") ULocale("fa_IR") else ULocale.forLocale(locale)).apply {
            timeZone = IcuTimeZone.getTimeZone(zoneId.id)
        }.format(java.util.Date(epochMillis))
        val date = "${fields.get(Calendar.DAY_OF_MONTH)} $monthName $year"
        return TextNormalizer.formatDigits("$date · $clock", numeralMode)
    }

    private fun fieldsToDate(year: Int, month: Int, day: Int, mode: CalendarDisplayMode, zone: ZoneId): LocalDate? = runCatching {
        val fields = calendar(mode, zone).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        Instant.ofEpochMilli(fields.timeInMillis).atZone(zone).toLocalDate()
    }.getOrNull()

    private fun calendar(mode: CalendarDisplayMode, zone: ZoneId): Calendar {
        val timeZone = IcuTimeZone.getTimeZone(zone.id)
        return when (mode) {
            CalendarDisplayMode.PERSIAN -> Calendar.getInstance(timeZone, persianLocale)
            CalendarDisplayMode.GREGORIAN -> Calendar.getInstance(timeZone, gregorianLocale)
            CalendarDisplayMode.ISLAMIC_CIVIL -> Calendar.getInstance(timeZone, islamicLocale)
        }
    }

    private fun monthName(month: Int, isPersian: Boolean, persian: List<String>, english: List<String>): String =
        (if (isPersian) persian else english).getOrElse(month) { "" }

    private val persianMonthsFa = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val persianMonthsEn = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    private val gregorianMonthsFa = listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    private val gregorianMonthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val islamicMonthsFa = listOf("محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه")
    private val islamicMonthsEn = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah")
}
