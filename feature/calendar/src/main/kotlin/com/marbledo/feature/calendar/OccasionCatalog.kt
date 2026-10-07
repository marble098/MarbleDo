package com.marbledo.feature.calendar

import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Category keys stay stable because they are persisted in the settings profile. */
enum class OccasionCategory(val key: String) {
    OFFICIAL("official"),
    NATIONAL("national"),
    RELIGIOUS("religious"),
    PERSONAL("personal"),
    ;

    companion object {
        fun fromKey(value: String): OccasionCategory? = entries.firstOrNull { it.key == value }
    }
}

/** Which calendar the fixed month/day of an occasion refers to. */
enum class OccasionCalendar(val key: String) {
    JALALI("jalali"),
    LUNAR("lunar"),
    GREGORIAN("gregorian"),
    ;

    companion object {
        fun fromKey(value: String): OccasionCalendar? = entries.firstOrNull { it.key == value }
    }
}

data class Occasion(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val category: OccasionCategory,
    val calendar: OccasionCalendar,
    val month: Int,
    val day: Int,
    val isHoliday: Boolean,
) {
    fun title(languageTag: String): String = if (languageTag == "fa") titleFa else titleEn
}

data class OccasionCatalog(
    val occasions: List<Occasion>,
    val dataVersion: String? = null,
) {
    companion object {
        val EMPTY = OccasionCatalog(emptyList(), null)

        /**
         * Parses both the current schema (`jalali`/`lunar`/`gregorian` arrays) and the original
         * `holidays` array, so older cached copies keep working.
         */
        fun parse(json: String): OccasionCatalog? = runCatching {
            val root = JSONObject(json)
            val occasions = buildList {
                addAll(readEntries(root.optJSONArray("jalali"), OccasionCalendar.JALALI))
                addAll(readEntries(root.optJSONArray("holidays"), OccasionCalendar.JALALI))
                addAll(readEntries(root.optJSONArray("lunar"), OccasionCalendar.LUNAR))
                addAll(readEntries(root.optJSONArray("gregorian"), OccasionCalendar.GREGORIAN))
            }.distinctBy { it.id }
            if (occasions.isEmpty()) null else OccasionCatalog(occasions, root.optString("updatedAt").takeIf { it.isNotBlank() })
        }.getOrNull()

        private fun readEntries(array: JSONArray?, calendar: OccasionCalendar): List<Occasion> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val month = item.optInt("month", 0)
                val day = item.optInt("day", 0)
                val category = OccasionCategory.fromKey(item.optString("category"))
                val titleFa = item.optString("titleFa").trim()
                val titleEn = item.optString("titleEn").trim()
                if (month !in 1..12 || day !in 1..31 || category == null) return@mapNotNull null
                if (titleFa.isBlank() && titleEn.isBlank()) return@mapNotNull null
                Occasion(
                    id = "${calendar.key}-$month-$day-${titleEn.ifBlank { titleFa }}",
                    titleFa = titleFa.ifBlank { titleEn },
                    titleEn = titleEn.ifBlank { titleFa },
                    category = category,
                    calendar = calendar,
                    month = month,
                    day = day,
                    isHoliday = item.optBoolean("holiday", false),
                )
            }
        }
    }
}

/**
 * Occasions resolved to concrete Jalali days for a set of Jalali years. Lunar occasions are
 * converted with Android's ICU Islamic calendar, so no per-year data download is required.
 */
class OccasionIndex private constructor(
    private val byYear: Map<Int, Map<Int, List<Occasion>>>,
) {
    val isEmpty: Boolean get() = byYear.isEmpty()

    fun on(jalaliYear: Int, month: Int, day: Int): List<Occasion> = byYear[jalaliYear]?.get(month * 100 + day).orEmpty()

    fun isHoliday(jalaliYear: Int, month: Int, day: Int): Boolean = on(jalaliYear, month, day).any(Occasion::isHoliday)

    fun forMonth(jalaliYear: Int, month: Int): List<Pair<Int, Occasion>> = byYear[jalaliYear].orEmpty()
        .filterKeys { it / 100 == month }
        .flatMap { (key, value) -> value.map { (key % 100) to it } }
        .sortedWith(compareBy({ it.first }, { it.second.titleFa }))

    companion object {
        val EMPTY = OccasionIndex(emptyMap())

        fun build(
            catalog: OccasionCatalog,
            jalaliYears: Collection<Int>,
            lunarOffsetDays: Int,
            enabledCategories: Set<OccasionCategory> = OccasionCategory.entries.toSet(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): OccasionIndex {
            if (catalog.occasions.isEmpty()) return EMPTY
            val eligible = catalog.occasions.filter { it.category in enabledCategories }
            if (eligible.isEmpty()) return EMPTY
            val offsetMillis = lunarOffsetDays.toLong() * DAY_MILLIS
            val byYear = HashMap<Int, Map<Int, List<Occasion>>>()
            jalaliYears.distinct().forEach { year ->
                val start = JalaliCalendarMath.jalaliEpoch(year, 1, 1, zone) ?: return@forEach
                val end = JalaliCalendarMath.jalaliEpoch(year + 1, 1, 1, zone) ?: return@forEach
                if (end <= start) return@forEach
                val gregorianYears = setOf(
                    JalaliCalendarMath.gregorianYear(start, zone),
                    JalaliCalendarMath.gregorianYear(end - 1, zone),
                )
                val lunarYears = setOf(
                    JalaliCalendarMath.lunarYear(start, zone),
                    JalaliCalendarMath.lunarYear(end - 1, zone),
                )
                val days = HashMap<Int, MutableList<Occasion>>()
                eligible.forEach { occasion ->
                    val epochs = when (occasion.calendar) {
                        OccasionCalendar.JALALI ->
                            listOfNotNull(JalaliCalendarMath.jalaliEpoch(year, occasion.month, occasion.day, zone))
                        OccasionCalendar.GREGORIAN ->
                            gregorianYears.mapNotNull { JalaliCalendarMath.gregorianEpoch(it, occasion.month, occasion.day, zone) }
                        OccasionCalendar.LUNAR ->
                            lunarYears.mapNotNull { JalaliCalendarMath.lunarEpoch(it, occasion.month, occasion.day, zone) }
                                .map { it - offsetMillis }
                    }
                    epochs.filter { it in start until end }.forEach { epoch ->
                        val fields = JalaliCalendarMath.jalaliFields(epoch, zone)
                        days.getOrPut(fields.month * 100 + fields.day) { mutableListOf() }.add(occasion)
                    }
                }
                byYear[year] = days
            }
            return OccasionIndex(byYear)
        }

        internal const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/** Shared ICU conversions. Kept internal so the UI layer only depends on [OccasionIndex]. */
internal object JalaliCalendarMath {
    const val PERSIAN_LOCALE = "fa_IR@calendar=persian"
    internal val persianLocale = ULocale(PERSIAN_LOCALE)
    private val gregorianLocale = ULocale("en_US@calendar=gregorian")
    private val islamicLocale = ULocale("ar@calendar=islamic-civil")

    data class Fields(val year: Int, val month: Int, val day: Int, val dayOfWeek: Int)

    fun persianCalendar(zone: ZoneId): Calendar = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), persianLocale)
    fun islamicCalendar(zone: ZoneId): Calendar = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), islamicLocale)
    private fun gregorianCalendar(zone: ZoneId): Calendar = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), gregorianLocale)

    fun jalaliFields(epochMillis: Long, zone: ZoneId): Fields {
        val calendar = persianCalendar(zone).apply { timeInMillis = epochMillis }
        return Fields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
        )
    }

    fun islamicFields(epochMillis: Long, zone: ZoneId): Fields {
        val calendar = islamicCalendar(zone).apply { timeInMillis = epochMillis }
        return Fields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
        )
    }

    fun gregorianYear(epochMillis: Long, zone: ZoneId): Int =
        gregorianCalendar(zone).apply { timeInMillis = epochMillis }.get(Calendar.YEAR)

    fun lunarYear(epochMillis: Long, zone: ZoneId): Int =
        islamicCalendar(zone).apply { timeInMillis = epochMillis }.get(Calendar.YEAR)

    /** 0 = Saturday through 6 = Friday, matching the Persian week. */
    fun saturdayBasedWeekday(icuDayOfWeek: Int): Int = icuDayOfWeek % 7

    fun jalaliEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(persianCalendar(zone), year, month, day)

    fun lunarEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(islamicCalendar(zone), year, month, day)

    internal fun gregorianEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(gregorianCalendar(zone), year, month, day)

    private fun strictEpoch(calendar: Calendar, year: Int, month: Int, day: Int): Long? = runCatching {
        calendar.apply {
            isLenient = false
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
    }.getOrNull()
}
