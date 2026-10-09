package com.marbledo.feature.countdown

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.time.ZoneId
import java.util.Locale

/** Calendar-aware display of countdown dates, using Android's public ICU APIs. Input is picked, never typed. */
object CountdownDateUtils {
    private val persianLocale = ULocale("fa_IR@calendar=persian")
    private val gregorianLocale = ULocale("en_US@calendar=gregorian")
    private val islamicLocale = ULocale("ar@calendar=islamic-civil")

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
