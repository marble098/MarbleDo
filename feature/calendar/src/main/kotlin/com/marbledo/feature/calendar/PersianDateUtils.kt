package com.marbledo.feature.calendar

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** A resolved Jalali date where [weekdayIndex] is 0 for Saturday through 6 for Friday. */
data class PersianDate(val year: Int, val month: Int, val day: Int, val weekdayIndex: Int)

/** Persian/Gregorian/Islamic date helpers shared by the calendar and the dashboard. */
object PersianDateUtils {
    private val persianMonthsFa = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private val persianMonthsEn = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    private val islamicMonthsFa = listOf("محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه")
    private val islamicMonthsEn = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah")
    private val weekdaysFa = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    private val weekdaysEn = listOf("Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

    fun today(zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = System.currentTimeMillis()): PersianDate = of(nowMillis, zone)

    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): PersianDate {
        val calendar = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), JalaliCalendarMath.persianLocale).apply {
            timeInMillis = epochMillis
        }
        return PersianDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            weekdayIndex = JalaliCalendarMath.saturdayBasedWeekday(calendar.get(Calendar.DAY_OF_WEEK)),
        )
    }

    fun startOfJalaliDay(year: Int, month: Int, day: Int, zone: ZoneId = ZoneId.systemDefault()): Long? =
        JalaliCalendarMath.jalaliEpoch(year, month, day, zone)

    fun monthName(month: Int, languageTag: String): String = monthNames(languageTag).getOrElse(month - 1) { "" }

    fun monthNames(languageTag: String): List<String> = if (languageTag == "fa") persianMonthsFa else persianMonthsEn

    fun weekdayName(weekdayIndex: Int, languageTag: String): String =
        (if (languageTag == "fa") weekdaysFa else weekdaysEn).getOrElse(weekdayIndex) { "" }

    fun weekdayShort(weekdayIndex: Int, languageTag: String): String {
        val name = weekdayName(weekdayIndex, languageTag)
        return if (languageTag == "fa") name.first().toString() else name
    }

    /** Example: `۱۲ آبان ۱۴۰۵`. */
    fun fullDate(epochMillis: Long, languageTag: String, numeralMode: NumeralMode, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = of(epochMillis, zone)
        return TextNormalizer.formatDigits("${date.day} ${monthName(date.month, languageTag)} ${date.year}", numeralMode)
    }

    /** Example: `دوشنبه ۱۲ آبان ۱۴۰۵`. */
    fun fullDateWithWeekday(epochMillis: Long, languageTag: String, numeralMode: NumeralMode, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = of(epochMillis, zone)
        return TextNormalizer.formatDigits("${weekdayName(date.weekdayIndex, languageTag)} ${date.day} ${monthName(date.month, languageTag)} ${date.year}", numeralMode)
    }

    fun gregorianDate(epochMillis: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, ULocale.forLocale(locale)).apply {
            timeZone = IcuTimeZone.getTimeZone(zone.id)
        }.format(Date(epochMillis))

    /** Example: `۱۲ آبان ۱۴۰۵  ·  2026/11/03  ·  ۲۳ ربیع‌الثانی ۱۴۴۸`. */
    fun tripleDate(
        epochMillis: Long,
        languageTag: String,
        numeralMode: NumeralMode,
        lunarOffsetDays: Int = 0,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val persian = fullDate(epochMillis, languageTag, NumeralMode.LATIN, zone)
        val gregorian = gregorianDate(epochMillis, locale, zone)
        val lunar = lunarDate(epochMillis, languageTag, NumeralMode.LATIN, lunarOffsetDays, zone)
        return TextNormalizer.formatDigits("$persian  ·  $gregorian  ·  $lunar", numeralMode)
    }

    fun lunarDate(
        epochMillis: Long,
        languageTag: String,
        numeralMode: NumeralMode = NumeralMode.LATIN,
        lunarOffsetDays: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val calendar = JalaliCalendarMath.islamicCalendar(zone).apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_MONTH, lunarOffsetDays)
        }
        val month = (if (languageTag == "fa") islamicMonthsFa else islamicMonthsEn).getOrElse(calendar.get(Calendar.MONTH)) { "" }
        return TextNormalizer.formatDigits("${calendar.get(Calendar.DAY_OF_MONTH)} $month ${calendar.get(Calendar.YEAR)}", numeralMode)
    }
}
