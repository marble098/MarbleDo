package com.marbledo.feature.calendar

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A one-tap plan that hands a chosen occasion over to the task editor on the dashboard.
 *
 * @param dateIso ISO date (yyyy-MM-dd) the new task should be scheduled on.
 * @param asCountdown when true the task is created as a live countdown instead of a plain to-do.
 */
data class OccasionPlanRequest(
    val title: String,
    val dateIso: String?,
    val asCountdown: Boolean,
    val checklist: List<String> = emptyList(),
    val description: String = "",
    val planningTag: String? = null,
)

/** An enabled occasion resolved to the next day it occurs on, used by "upcoming occasions" lists. */
data class UpcomingOccasion(
    val occasion: Occasion,
    val jalaliYear: Int,
    val month: Int,
    val day: Int,
    val epochMillis: Long,
    val daysUntil: Long,
    val isFavorite: Boolean,
)

/** How complete the loaded catalog is; shown in settings so a gap is visible instead of silent. */
data class CatalogCoverage(
    val total: Int,
    val perCalendar: Map<OccasionCalendar, Int>,
    val perCategory: Map<OccasionCategory, Int>,
    val monthsPerCalendar: Map<OccasionCalendar, Set<Int>>,
) {
    val coveredMonths: Int get() = monthsPerCalendar.values.maxOfOrNull { it.size } ?: 0

    val missingMonths: List<Pair<OccasionCalendar, Int>> get() = OccasionCalendar.entries.flatMap { calendar ->
        val covered = monthsPerCalendar[calendar].orEmpty()
        (1..12).filter { it !in covered }.map { calendar to it }
    }

    val isFullYear: Boolean get() = missingMonths.isEmpty()

    val categoryCount: Int get() = perCategory.count { it.value > 0 }
}

/** Occasion helpers that are shared by the calendar, the dashboard and the settings screen. */
object OccasionPlanner {
    /** Occasions the user starred, in catalog order. */
    fun favorites(catalog: OccasionCatalog, favoriteIds: Set<String>): List<Occasion> =
        catalog.occasions.filter { it.id in favoriteIds }

    /** Builds the request for a task on the occasion day, a preparation task, or a countdown. */
    fun planRequest(
        occasion: Occasion,
        epochMillis: Long,
        languageTag: String,
        daysBefore: Int = 0,
        asCountdown: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): OccasionPlanRequest {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            .minusDays(daysBefore.coerceAtLeast(0).toLong())
        return OccasionPlanRequest(
            title = occasion.title(languageTag),
            dateIso = date.toString(),
            asCountdown = asCountdown,
            planningTag = OccasionIntelligence.planningTag(occasion.id, Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()),
        )
    }

    /**
     * The next occurrences of the enabled occasions, today included. Pass [favoritesOnly] to list
     * only starred occasions, which is how the dashboard surfaces "my important occasions".
     */
    fun upcoming(
        index: OccasionIndex,
        jalaliYears: Collection<Int>,
        nowEpochMillis: Long,
        favoriteIds: Set<String>,
        favoritesOnly: Boolean,
        limit: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<UpcomingOccasion> {
        if (limit <= 0) return emptyList()
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val result = mutableListOf<UpcomingOccasion>()
        jalaliYears.distinct().sorted().forEach { year ->
            index.forYear(year).forEach { dated ->
                val occasion = dated.occasion
                val isFavorite = occasion.id in favoriteIds
                if (favoritesOnly && !isFavorite) return@forEach
                val epochMillis = PersianDateUtils.startOfJalaliDay(year, dated.month, dated.day, zone) ?: return@forEach
                val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
                val daysUntil = ChronoUnit.DAYS.between(today, date)
                if (daysUntil < 0) return@forEach
                result += UpcomingOccasion(
                    occasion = occasion,
                    jalaliYear = year,
                    month = dated.month,
                    day = dated.day,
                    epochMillis = epochMillis,
                    daysUntil = daysUntil,
                    isFavorite = isFavorite,
                )
            }
        }
        return result
            .sortedWith(compareBy({ it.daysUntil }, { it.epochMillis }, { it.occasion.titleFa }))
            .distinctBy { "${it.occasion.id}-${it.jalaliYear}-${it.month}-${it.day}" }
            .take(limit)
    }

    /** Public closures remain relevant to preparation even when their discovery pack is hidden. */
    fun holidayDates(catalog: OccasionCatalog, years: List<Int>, lunarOffsetDays: Int, zone: ZoneId): Set<java.time.LocalDate> {
        val index = OccasionIndex.build(catalog, years, lunarOffsetDays, OccasionCategory.publicCategories, zone)
        return years.flatMap { index.forYear(it) }.filter { it.occasion.isHoliday }.mapNotNull { row ->
            PersianDateUtils.startOfJalaliDay(row.year, row.month, row.day, zone)?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
        }.toSet()
    }

    /** Catalogue statistics for the settings "coverage" line. */
    fun coverage(catalog: OccasionCatalog): CatalogCoverage {
        val months = mutableMapOf<OccasionCalendar, MutableSet<Int>>()
        val perCalendar = mutableMapOf<OccasionCalendar, Int>()
        val perCategory = mutableMapOf<OccasionCategory, Int>()
        catalog.occasions.forEach { occasion ->
            perCalendar[occasion.calendar] = (perCalendar[occasion.calendar] ?: 0) + 1
            perCategory[occasion.category] = (perCategory[occasion.category] ?: 0) + 1
            months.getOrPut(occasion.calendar) { mutableSetOf() } += occasion.month
        }
        return CatalogCoverage(
            total = catalog.occasions.size,
            perCalendar = perCalendar,
            perCategory = perCategory,
            monthsPerCalendar = months,
        )
    }
}
