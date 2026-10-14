package com.marbledo.feature.calendar

import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Persisted category keys. The school layer must be explicitly enabled on every surface. */
enum class OccasionCategory(val key: String) {
    OFFICIAL("official"), NATIONAL("national"), RELIGIOUS("religious"), PERSONAL("personal"),
    INTERNATIONAL("international"), EDUCATIONAL("educational");

    companion object {
        val publicCategories: Set<OccasionCategory> = entries.filterNot { it == EDUCATIONAL }.toSet()
        fun fromKey(value: String): OccasionCategory? = entries.firstOrNull { it.key == value }
    }
}

enum class OccasionCalendar(val key: String) {
    JALALI("jalali"), LUNAR("lunar"), GREGORIAN("gregorian");
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
    val sourceIds: List<String> = emptyList(),
    val evidence: OccasionEvidence = OccasionEvidence.LEGACY,
    val noteFa: String = "",
    val noteEn: String = "",
    val tags: Set<String> = emptySet(),
    val dateRule: OccasionDateRule = OccasionDateRule.FIXED,
    val weekday: Int? = null,
    val ordinal: Int? = null,
    val dayOffset: Int = 0,
    val durationDays: Int = 1,
) {
    fun title(languageTag: String): String = if (languageTag == "fa") titleFa else titleEn
    fun note(languageTag: String): String = if (languageTag == "fa") noteFa else noteEn
    val isEducational: Boolean get() = category == OccasionCategory.EDUCATIONAL || "education" in tags
}

data class YearlyOverride(
    val occasions: List<Occasion> = emptyList(),
    val removedTitlesEn: Set<String> = emptySet(),
)

data class OccasionCatalog(
    val occasions: List<Occasion>,
    val dataVersion: String? = null,
    val yearly: Map<Int, YearlyOverride> = emptyMap(),
    val schemaVersion: Int = 1,
    val updatedAt: String? = null,
    val sources: List<CatalogReference> = emptyList(),
    val schoolPrograms: List<SchoolProgram> = emptyList(),
    val idAliases: Map<String, String> = emptyMap(),
) {
    fun canonicalFavoriteIds(ids: Collection<String>): Set<String> = ids.map { id -> idAliases[id] ?: id }.toSet()

    companion object {
        const val SUPPORTED_SCHEMA = 4
        val EMPTY = OccasionCatalog(emptyList())

        /** Reject a partial/malformed update as a whole; never silently drop its bad rows. */
        fun parse(json: String): OccasionCatalog? = runCatching {
            val root = JSONObject(json)
            val schema = root.optInt("schemaVersion", 1)
            require(schema in 1..SUPPORTED_SCHEMA)
            if (schema >= 4) {
                OccasionCalendar.entries.forEach { require(root.opt(it.key) is JSONArray) }
                require(root.opt("sources") is JSONArray && root.opt("schoolPrograms") is JSONArray)
                java.time.LocalDate.parse(root.getString("updatedAt"))
            }
            val sources = objects(root.optJSONArray("sources")).map {
                CatalogReference(it.getString("id"), it.getString("titleFa"), it.getString("titleEn"),
                    it.getString("url"), it.getString("kind"), it.getString("checkedAt")).also { ref ->
                    require(ref.id.isNotBlank())
                    val uri = java.net.URI(ref.url)
                    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
                    require(ref.titleFa.isNotBlank() && ref.titleEn.isNotBlank())
                    java.time.LocalDate.parse(ref.checkedAt)
                }
            }
            require(sources.map { it.id }.distinct().size == sources.size)
            val occasions = readCalendars(root)
            require(occasions.isNotEmpty() && occasions.map { it.id }.distinct().size == occasions.size)
            val yearly = buildMap {
                val years = root.optJSONObject("yearly")
                years?.keys()?.forEach { key ->
                    val year = key.toInt()
                    require(year in 1200..1700)
                    val body = years.getJSONObject(key)
                    val entries = readCalendars(body)
                    require(entries.map { it.id }.distinct().size == entries.size)
                    put(year, YearlyOverride(entries, strings(body.optJSONArray("removeTitlesEn")).toSet()))
                }
            }
            val programs = objects(root.optJSONArray("schoolPrograms")).map {
                require(listOf("month", "day", "date", "calendar").none(it::has))
                SchoolProgram(it.getString("id"), it.getString("titleFa"), it.getString("titleEn"),
                    it.getString("noteFa"), it.getString("noteEn"), strings(it.getJSONArray("sourceIds")))
            }
            require(programs.map { it.id }.distinct().size == programs.size)
            val sourceIds = sources.map { it.id }.toSet()
            if (schema >= 4) {
                (occasions + yearly.values.flatMap { it.occasions }).forEach {
                    require(it.sourceIds.isNotEmpty() && it.sourceIds.all { id -> id in sourceIds })
                    if (it.category == OccasionCategory.EDUCATIONAL || it.evidence == OccasionEvidence.GUIDANCE) require(!it.isHoliday)
                }
                require(programs.all { it.id.isNotBlank() && it.titleFa.isNotBlank() && it.titleEn.isNotBlank() &&
                    it.noteFa.isNotBlank() && it.noteEn.isNotBlank() &&
                    it.sourceIds.isNotEmpty() && it.sourceIds.all { id -> id in sourceIds } })
            }
            val aliases = buildMap {
                val body = root.optJSONObject("idAliases")
                body?.keys()?.forEach { key -> put(key, body.getString(key)) }
            }
            val ids = occasions.map { it.id }.toSet()
            require(aliases.all { (old, target) -> old.isNotBlank() && old !in ids && old != target && target in ids })
            OccasionCatalog(
                occasions = occasions,
                dataVersion = root.optString("dataVersion").takeIf { it.isNotBlank() },
                yearly = yearly,
                schemaVersion = schema,
                updatedAt = root.optString("updatedAt").takeIf { it.isNotBlank() },
                sources = sources,
                schoolPrograms = programs,
                idAliases = aliases,
            )
        }.getOrNull()

        private fun objects(array: JSONArray?): List<JSONObject> =
            if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }

        private fun strings(array: JSONArray?): List<String> =
            if (array == null) emptyList() else (0 until array.length()).map { array.getString(it).trim().also { s -> require(s.isNotEmpty()) } }

        private fun readCalendars(root: JSONObject): List<Occasion> = buildList {
            OccasionCalendar.entries.forEach { addAll(readEntries(root.optJSONArray(it.key), it)) }
            addAll(readEntries(root.optJSONArray("holidays"), OccasionCalendar.JALALI))
        }

        private fun readEntries(array: JSONArray?, calendar: OccasionCalendar): List<Occasion> = objects(array).map { item ->
            val month = item.getInt("month")
            val day = item.getInt("day")
            require(month in 1..12)
            val maxDay = when (calendar) {
                OccasionCalendar.JALALI -> if (month <= 6) 31 else 30
                OccasionCalendar.LUNAR -> 30
                OccasionCalendar.GREGORIAN -> java.time.Month.of(month).maxLength()
            }
            require(day in 1..maxDay)
            val category = requireNotNull(OccasionCategory.fromKey(item.getString("category")))
            val fa = item.optString("titleFa").trim()
            val en = item.optString("titleEn").trim()
            require(fa.isNotBlank() || en.isNotBlank())
            val rule = requireNotNull(OccasionDateRule.entries.firstOrNull { it.key == item.optString("dateRule", "fixed") })
            val weekday = if (item.has("weekday")) item.getInt("weekday") else null
            val ordinal = if (item.has("ordinal")) item.getInt("ordinal") else null
            val offset = item.optInt("dayOffset", 0)
            val duration = item.optInt("durationDays", 1)
            require(offset in -7..7 && duration in 1..31)
            if (rule == OccasionDateRule.NTH_WEEKDAY || rule == OccasionDateRule.LAST_WEEKDAY) require(weekday != null && weekday in 1..7)
            if (rule == OccasionDateRule.NTH_WEEKDAY) require(ordinal != null && ordinal in 1..5)
            Occasion(
                id = item.optString("id").trim().ifBlank { "${calendar.key}-$month-$day-${en.ifBlank { fa }}" },
                titleFa = fa.ifBlank { en }, titleEn = en.ifBlank { fa }, category = category,
                calendar = calendar, month = month, day = day, isHoliday = item.optBoolean("holiday", false),
                sourceIds = strings(item.optJSONArray("sourceIds")),
                evidence = requireNotNull(OccasionEvidence.entries.firstOrNull { it.key == item.optString("evidence", "legacy") }),
                noteFa = item.optString("noteFa"), noteEn = item.optString("noteEn"),
                tags = strings(item.optJSONArray("tags")).toSet(), dateRule = rule, weekday = weekday,
                ordinal = ordinal, dayOffset = offset, durationDays = duration,
            )
        }
    }
}

/** Includes the year so academic-year lists and export keys remain unambiguous across Nowruz. */
data class DatedOccasion(val month: Int, val day: Int, val occasion: Occasion, val year: Int = 0)

class OccasionIndex private constructor(private val byYear: Map<Int, Map<Int, List<Occasion>>>) {
    val isEmpty: Boolean get() = byYear.isEmpty()
    fun on(jalaliYear: Int, month: Int, day: Int): List<Occasion> = byYear[jalaliYear]?.get(month * 100 + day).orEmpty()
    fun isHoliday(jalaliYear: Int, month: Int, day: Int): Boolean = on(jalaliYear, month, day).any(Occasion::isHoliday)
    fun forYear(jalaliYear: Int): List<DatedOccasion> = byYear[jalaliYear].orEmpty()
        .flatMap { (key, rows) -> rows.map { DatedOccasion(key / 100, key % 100, it, jalaliYear) } }
        .sortedWith(compareBy({ it.month }, { it.day }, { it.occasion.titleFa }))
    fun forMonth(jalaliYear: Int, month: Int): List<Pair<Int, Occasion>> = forYear(jalaliYear)
        .filter { it.month == month }.map { it.day to it.occasion }

    companion object {
        val EMPTY = OccasionIndex(emptyMap())

        private data class CacheKey(
            val catalogIdentity: Int,
            val catalogVersion: Int,
            val occasionCount: Int,
            val years: List<Int>,
            val lunarOffsetDays: Int,
            val enabledCategories: Set<OccasionCategory>,
            val zoneId: String,
        )

        private val buildCache = object : LinkedHashMap<CacheKey, OccasionIndex>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, OccasionIndex>?): Boolean = size > 16
        }

        fun build(
            catalog: OccasionCatalog,
            jalaliYears: Collection<Int>,
            lunarOffsetDays: Int,
            enabledCategories: Set<OccasionCategory> = OccasionCategory.publicCategories,
            zone: ZoneId = ZoneId.systemDefault(),
        ): OccasionIndex {
            if (catalog.occasions.isEmpty() && catalog.yearly.isEmpty()) return EMPTY
            val sortedYears = jalaliYears.distinct().sorted()
            if (sortedYears.isEmpty()) return EMPTY
            val key = CacheKey(
                catalogIdentity = System.identityHashCode(catalog),
                catalogVersion = catalog.schemaVersion,
                occasionCount = catalog.occasions.size,
                years = sortedYears,
                lunarOffsetDays = lunarOffsetDays.coerceIn(-2, 2),
                enabledCategories = enabledCategories,
                zoneId = zone.id,
            )
            synchronized(buildCache) {
                buildCache[key]?.let { return it }
            }
            val persian = JalaliCalendarMath.persianCalendar(zone)
            val gregorian = JalaliCalendarMath.gregorianCalendar(zone)
            val islamic = JalaliCalendarMath.islamicCalendar(zone)
            fun local(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
            val byYear = mutableMapOf<Int, Map<Int, List<Occasion>>>()
            sortedYears.forEach { year ->
                val startEpoch = JalaliCalendarMath.strictEpoch(persian, year, 1, 1) ?: return@forEach
                val endEpoch = JalaliCalendarMath.strictEpoch(persian, year + 1, 1, 1) ?: return@forEach
                val start = local(startEpoch)
                val end = local(endEpoch)
                val override = catalog.yearly[year]
                val eligible = (catalog.occasions.filter { it.titleEn !in override?.removedTitlesEn.orEmpty() } + override?.occasions.orEmpty())
                    .filter { it.category in enabledCategories }.distinctBy { it.id }
                val days = mutableMapOf<Int, MutableList<Occasion>>()
                eligible.forEach occasionLoop@ { occasion ->
                    val calendar = when (occasion.calendar) {
                        OccasionCalendar.JALALI -> persian
                        OccasionCalendar.LUNAR -> islamic
                        OccasionCalendar.GREGORIAN -> gregorian
                    }
                    val offset = if (occasion.calendar == OccasionCalendar.LUNAR) lunarOffsetDays.coerceIn(-2, 2) else 0
                    // Include every intervening lunar year (a solar year can touch THREE lunar years),
                    // and both edges after adjustment. No fixed 24-hour shifts across DST boundaries.
                    val firstEpoch = start.plusDays(offset - 7L).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                    val lastEpoch = end.plusDays(offset + 7L).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                    val years = if (occasion.calendar == OccasionCalendar.JALALI) year..year else
                        JalaliCalendarMath.yearOf(calendar, firstEpoch)..JalaliCalendarMath.yearOf(calendar, lastEpoch)
                    years.forEach yearLoop@ { sourceYear ->
                        val first = JalaliCalendarMath.strictEpoch(calendar, sourceYear, occasion.month, 1) ?: return@yearLoop
                        val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val resolved = OccasionDateRules.resolve(
                            firstDay = local(first).minusDays(offset.toLong()), monthLength = length, day = occasion.day,
                            rule = occasion.dateRule, weekday = occasion.weekday, ordinal = occasion.ordinal, dayOffset = occasion.dayOffset,
                        ) ?: return@yearLoop
                        if (resolved < start || resolved >= end) return@yearLoop
                        val fields = JalaliCalendarMath.jalaliFieldsIn(persian, resolved.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
                        days.getOrPut(fields.month * 100 + fields.day) { mutableListOf() }.add(occasion)
                    }
                }
                byYear[year] = days
            }
            val result = OccasionIndex(byYear)
            synchronized(buildCache) {
                buildCache[key] = result
            }
            return result
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
