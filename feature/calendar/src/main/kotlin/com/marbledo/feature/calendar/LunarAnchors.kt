package com.marbledo.feature.calendar

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Iran's official lunar month table (schema 6 `lunarAnchors`).
 *
 * Android's `islamic-civil` calendar is arithmetic. The official Iranian calendar follows the
 * announced crescent sighting, so the two disagree on individual months - and the arithmetic
 * calendar is the reason "3 Jumada al-Thani 1448" used to be shown as 23 Aban 1405 while the
 * statutory day off is 22 Aban 1405. Lunar occasions are resolved from this table instead:
 * `startDate` is 1 Muharram of `startHijriYear`, `officialLengths` are the published month
 * lengths after it, and `estimatedLengths` are estimates for months the official calendar has
 * not published yet. An empty or absent table keeps the old arithmetic behaviour.
 */
class LunarAnchors internal constructor(
    val startHijriYear: Int,
    val startMonth: Int,
    val startDate: LocalDate,
    officialLengths: IntArray,
    estimatedLengths: IntArray,
) {
    private val officialCount: Int = officialLengths.size
    private val lengths: IntArray = officialLengths + estimatedLengths
    private val offsets: IntArray = IntArray(lengths.size + 1).also { cumulative ->
        var total = 0
        cumulative[0] = 0
        for (index in lengths.indices) {
            total += lengths[index]
            cumulative[index + 1] = total
        }
    }

    init {
        require(startMonth in 1..12) { "Invalid anchor month" }
        require(officialLengths.isNotEmpty()) { "The official month table cannot be empty" }
        require(lengths.all { it == 29 || it == 30 }) { "Lunar months have 29 or 30 days" }
    }

    val monthCount: Int get() = lengths.size

    /** The last month whose length comes from the published official calendar. */
    fun officialThrough(): Pair<Int, Int>? {
        if (officialCount == 0) return null
        var year = startHijriYear
        var month = startMonth
        repeat(officialCount - 1) {
            if (month == 12) { year += 1; month = 1 } else month += 1
        }
        return year to month
    }

    /** First day of a Hijri month, or null when the table does not cover it. */
    fun monthStart(hijriYear: Int, month: Int): LocalDate? {
        val index = indexOf(hijriYear, month)
        return if (index < 0) null else startDate.plusDays(offsets[index].toLong())
    }

    /** Length of a Hijri month in days, or null when the table does not cover it. */
    fun monthLength(hijriYear: Int, month: Int): Int? {
        val index = indexOf(hijriYear, month)
        return if (index < 0) null else lengths[index]
    }

    /** True when the month is an estimate rather than a published official length. */
    fun isEstimated(hijriYear: Int, month: Int): Boolean = indexOf(hijriYear, month) >= officialCount

    private fun indexOf(hijriYear: Int, month: Int): Int {
        if (month !in 1..12) return -1
        var yearDelta = hijriYear - startHijriYear
        var monthDelta = month - startMonth
        if (monthDelta < 0) {
            monthDelta += 12
            yearDelta -= 1
        }
        if (yearDelta < 0) return -1
        val index = yearDelta * 12 + monthDelta
        return if (index in lengths.indices) index else -1
    }

    companion object {
        /** A table covering [startYear]/[startMonth] onwards; used by tests and the parser. */
        fun of(
            startYear: Int,
            startMonth: Int,
            startDate: LocalDate,
            officialLengths: IntArray,
            estimatedLengths: IntArray = IntArray(0),
        ): LunarAnchors = LunarAnchors(startYear, startMonth, startDate, officialLengths, estimatedLengths)

        /** A null return means "no usable table"; the caller falls back to the arithmetic calendar. */
        fun parse(json: JSONObject?): LunarAnchors? {
            if (json == null) return null
            return runCatching {
                val official = ints(json.getJSONArray("officialLengths"))
                val estimated = ints(json.optJSONArray("estimatedLengths"))
                require(official.isNotEmpty())
                LunarAnchors(
                    startHijriYear = json.getInt("startHijriYear"),
                    startMonth = json.optInt("startMonth", 1),
                    startDate = LocalDate.parse(json.getString("startDate")),
                    officialLengths = official,
                    estimatedLengths = estimated,
                )
            }.getOrNull()
        }

        private fun ints(array: JSONArray?): IntArray {
            if (array == null) return IntArray(0)
            return IntArray(array.length()) { index -> array.getInt(index) }
        }
    }
}
