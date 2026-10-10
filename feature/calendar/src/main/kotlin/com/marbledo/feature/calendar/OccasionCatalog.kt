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
    INTERNATIONAL("international"),
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

/**
 * Changes applied on top of the base catalog for one Jalali year: extra occasions that only exist
 * that year, and English titles of base occasions that should not appear that year.
 */
data class YearlyOverride(
    val occasions: List<Occasion> = emptyList(),
    val removedTitlesEn: Set<String> = emptySet(),
)

data class OccasionCatalog(
    val occasions: List<Occasion>,
    val dataVersion: String? = null,
    val yearly: Map<Int, YearlyOverride> = emptyMap(),
) {
    companion object {
        val EMPTY = OccasionCatalog(emptyList(), null)

        /**
         * Parses both the current schema (`jalali`/`lunar`/`gregorian` arrays, optional `yearly`
         * overrides) and the original `holidays` array, so older cached copies keep working.
         */
        fun parse(json: String): OccasionCatalog? = runCatching {
            val root = JSONObject(json)
            val occasions = buildList {
                addAll(readEntries(root.optJSONArray("jalali"), OccasionCalendar.JALALI))
                addAll(readEntries(root.optJSONArray("holidays"), OccasionCalendar.JALALI))
                addAll(readEntries(root.optJSONArray("lunar"), OccasionCalendar.LUNAR))
                addAll(readEntries(root.optJSONArray("gregorian"), OccasionCalendar.GREGORIAN))
            }.distinctBy { it.id }
            val version = root.optString("dataVersion").takeIf { it.isNotBlank() }
                ?: root.optString("updatedAt").takeIf { it.isNotBlank() }
            val yearly = readYearly(root.optJSONObject("yearly"))
            if (occasions.isEmpty()) null else OccasionCatalog(occasions, version, yearly)
        }.getOrNull()

        private fun readYearly(json: JSONObject?): Map<Int, YearlyOverride> {
            if (json == null) return emptyMap()
            return buildMap {
                json.keys().forEach { yearKey ->
                    val year = yearKey.toIntOrNull() ?: return@forEach
                    val body = json.optJSONObject(yearKey) ?: return@forEach
                    val occasions = buildList {
                        addAll(readEntries(body.optJSONArray("jalali"), OccasionCalendar.JALALI))
                        addAll(readEntries(body.optJSONArray("lunar"), OccasionCalendar.LUNAR))
                        addAll(readEntries(body.optJSONArray("gregorian"), OccasionCalendar.GREGORIAN))
                    }
                    val removed = body.optJSONArray("removeTitlesEn")?.let { array ->
                        (0 until array.length())
                            .mapNotNull { array.optString(it).trim().takeIf(String::isNotEmpty) }
                            .toSet()
                    }.orEmpty()
                    if (occasions.isNotEmpty() || removed.isNotEmpty()) {
                        put(year, YearlyOverride(occasions, removed))
                    }
                }
            }
        }

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
data class DatedOccasion(val month: Int, val day: Int, val occasion: Occasion)

class OccasionIndex private constructor(
    private val byYear: Map<Int, Map<Int, List<Occasion>>>,
) {
    val isEmpty: Boolean get() = byYear.isEmpty()

    fun on(jalaliYear: Int, month: Int, day: Int): List<Occasion> = byYear[jalaliYear]?.get(month * 100 + day).orEmpty()

    fun isHoliday(jalaliYear: Int, month: Int, day: Int): Boolean = on(jalaliYear, month, day).any(Occasion::isHoliday)

    /** Every enabled occasion resolved to its actual Jalali day for easy whole-year browsing. */
    fun forYear(jalaliYear: Int): List<DatedOccasion> = byYear[jalaliYear].orEmpty()
        .flatMap { (key, occasions) -> occasions.map { DatedOccasion(key / 100, key % 100, it) } }
        .sortedWith(compareBy({ it.month }, { it.day }, { it.occasion.titleFa }))

    fun forMonth(jalaliYear: Int, month: Int): List<Pair<Int, Occasion>> = forYear(jalaliYear)
        .filter { it.month == month }
        .map { it.day to it.occasion }

    companion object {
        val EMPTY = OccasionIndex(emptyMap())

        fun build(
            catalog: OccasionCatalog,
            jalaliYears: Collection<Int>,
            lunarOffsetDays: Int,
            enabledCategories: Set<OccasionCategory> = OccasionCategory.entries.toSet(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): OccasionIndex {
            val hasEligible = catalog.occasions.any { it.category in enabledCategories } ||
                catalog.yearly.values.any { yearOverride -> yearOverride.occasions.any { it.category in enabledCategories } }
            if (!hasEligible) return EMPTY
            val offsetMillis = lunarOffsetDays.toLong() * DAY_MILLIS
            // One ICU calendar per system for the whole build: creating calendars per lookup is slow
            // with a catalog of several hundred occasions.
            val persian = JalaliCalendarMath.persianCalendar(zone)
            val gregorian = JalaliCalendarMath.gregorianCalendar(zone)
            val islamic = JalaliCalendarMath.islamicCalendar(zone)
            val byYear = HashMap<Int, Map<Int, List<Occasion>>>()
            jalaliYears.distinct().forEach { year ->
                val start = JalaliCalendarMath.strictEpoch(persian, year, 1, 1) ?: return@forEach
                val end = JalaliCalendarMath.strictEpoch(persian, year + 1, 1, 1) ?: return@forEach
                if (end <= start) return@forEach
                val gregorianYears = setOf(
                    JalaliCalendarMath.yearOf(gregorian, start),
                    JalaliCalendarMath.yearOf(gregorian, end - 1),
                )
                val lunarYears = setOf(
                    JalaliCalendarMath.yearOf(islamic, start),
                    JalaliCalendarMath.yearOf(islamic, end - 1),
                )
                val yearOverride = catalog.yearly[year]
                val removed = yearOverride?.removedTitlesEn.orEmpty()
                val eligible = (catalog.occasions.filter { it.titleEn !in removed } + yearOverride?.occasions.orEmpty())
                    .filter { it.category in enabledCategories }
                val days = HashMap<Int, MutableList<Occasion>>()
                eligible.forEach { occasion ->
                    val epochs = when (occasion.calendar) {
                        OccasionCalendar.JALALI ->
                            listOfNotNull(JalaliCalendarMath.strictEpoch(persian, year, occasion.month, occasion.day))
                        OccasionCalendar.GREGORIAN ->
                            gregorianYears.mapNotNull { JalaliCalendarMath.strictEpoch(gregorian, it, occasion.month, occasion.day) }
                        OccasionCalendar.LUNAR ->
                            lunarYears.mapNotNull { JalaliCalendarMath.strictEpoch(islamic, it, occasion.month, occasion.day) }
                                .map { it - offsetMillis }
                    }
                    epochs.filter { it in start until end }.forEach { epoch ->
                        val fields = JalaliCalendarMath.jalaliFieldsIn(persian, epoch)
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
    fun gregorianCalendar(zone: ZoneId): Calendar = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), gregorianLocale)

    fun jalaliFields(epochMillis: Long, zone: ZoneId): Fields = fieldsIn(persianCalendar(zone), epochMillis)

    /** Same as [jalaliFields] but reuses a calendar owned by the caller. */
    fun jalaliFieldsIn(calendar: Calendar, epochMillis: Long): Fields = fieldsIn(calendar, epochMillis)

    fun islamicFields(epochMillis: Long, zone: ZoneId): Fields = fieldsIn(islamicCalendar(zone), epochMillis)

    private fun fieldsIn(calendar: Calendar, epochMillis: Long): Fields {
        calendar.timeInMillis = epochMillis
        return Fields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
        )
    }

    fun gregorianYear(epochMillis: Long, zone: ZoneId): Int = yearOf(gregorianCalendar(zone), epochMillis)

    fun lunarYear(epochMillis: Long, zone: ZoneId): Int = yearOf(islamicCalendar(zone), epochMillis)

    /** Year of [epochMillis] in the calendar's own system; the calendar is reused by the caller. */
    fun yearOf(calendar: Calendar, epochMillis: Long): Int {
        calendar.timeInMillis = epochMillis
        return calendar.get(Calendar.YEAR)
    }

    /** 0 = Saturday through 6 = Friday, matching the Persian week. */
    fun saturdayBasedWeekday(icuDayOfWeek: Int): Int = icuDayOfWeek % 7

    fun jalaliEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(persianCalendar(zone), year, month, day)

    fun lunarEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(islamicCalendar(zone), year, month, day)

    internal fun gregorianEpoch(year: Int, month: Int, day: Int, zone: ZoneId): Long? = strictEpoch(gregorianCalendar(zone), year, month, day)

    /** Strict conversion of a calendar date to epoch millis; null when the date does not exist. */
    internal fun strictEpoch(calendar: Calendar, year: Int, month: Int, day: Int): Long? = runCatching {
        calendar.apply {
            isLenient = false
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
    }.getOrNull()
}
