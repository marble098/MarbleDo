package com.marbledo.feature.tasks

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.util.Date
import java.util.Locale

object TaskDateLabels {
    fun tripleDate(epochMillis: Long, locale: Locale = Locale.getDefault(), numeralMode: NumeralMode = NumeralMode.PERSIAN): String {
        val timeZone = IcuTimeZone.getDefault()
        val instant = Date(epochMillis)
        val persian = persianCalendar(timeZone).apply { timeInMillis = epochMillis }
        val islamic = islamicCivilCalendar(timeZone).apply { timeInMillis = epochMillis }
        val gregorian = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { this.timeZone = timeZone }
        val gregorianText = gregorian.format(instant)
        val jalaliText = "${persian.get(Calendar.DAY_OF_MONTH)} ${persianMonthName(persian.get(Calendar.MONTH), locale)} ${persian.get(Calendar.YEAR)}"
        val lunarText = "${islamic.get(Calendar.DAY_OF_MONTH)} ${islamicMonthName(islamic.get(Calendar.MONTH), locale)} ${islamic.get(Calendar.YEAR)}"
        return TextNormalizer.formatDigits("$jalaliText  ·  $gregorianText  ·  $lunarText", numeralMode)
    }

    fun persianMonthName(month: Int, locale: Locale = Locale("fa")): String {
        val namesFa = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
        val namesEn = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
        return (if (locale.language == "fa") namesFa else namesEn).getOrElse(month) { "" }
    }

    private fun islamicMonthName(month: Int, locale: Locale): String {
        val namesFa = listOf("محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه")
        val namesEn = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah")
        return (if (locale.language == "fa") namesFa else namesEn).getOrElse(month) { "" }
    }
}
