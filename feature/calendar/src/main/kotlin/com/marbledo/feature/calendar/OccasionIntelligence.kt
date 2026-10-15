package com.marbledo.feature.calendar

import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local, explainable rules only. No network AI, tracking, or hidden automatic task creation. */
enum class OccasionRecommendationReason { FAVORITE, TODAY, APPROACHING, HOLIDAY, DISCOVER }
data class OccasionRecommendation(
    val upcoming: UpcomingOccasion,
    val reason: OccasionRecommendationReason,
    val alreadyPlanned: Boolean,
    val preparationDays: Int,
)

object OccasionIntelligence {
    fun planningTag(occasionId: String, date: LocalDate): String = "occasion:$occasionId:$date"

    fun isPlanned(occasion: Occasion, date: LocalDate, tasks: List<Task>, zone: ZoneId): Boolean {
        val tag = planningTag(occasion.id, date)
        val names = setOf(TextNormalizer.searchKey(occasion.titleFa), TextNormalizer.searchKey(occasion.titleEn))
        return tasks.any { task ->
            !task.isArchived && (tag in task.tags || (TextNormalizer.searchKey(task.title) in names &&
                task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date } == true))
        }
    }

    fun recommendations(
        upcoming: List<UpcomingOccasion>,
        tasks: List<Task>,
        zone: ZoneId,
        horizonDays: Int = 30,
        limit: Int = 5,
    ): List<OccasionRecommendation> {
        require(horizonDays in 1..366)
        if (limit <= 0) return emptyList()
        return upcoming.filter { it.daysUntil in 0..horizonDays.toLong() }
            .sortedWith(compareBy<UpcomingOccasion>(
                { if (it.isFavorite) 0 else 1 },
                { it.daysUntil },
                { if (it.occasion.isHoliday) 0 else 1 },
                { it.occasion.id },
            ))
            .distinctBy { it.occasion.id }
            .take(limit)
            .map { row ->
                val date = Instant.ofEpochMilli(row.epochMillis).atZone(zone).toLocalDate()
                OccasionRecommendation(
                    row,
                    when {
                        row.isFavorite -> OccasionRecommendationReason.FAVORITE
                        row.daysUntil == 0L -> OccasionRecommendationReason.TODAY
                        row.daysUntil <= 7 -> OccasionRecommendationReason.APPROACHING
                        row.occasion.isHoliday -> OccasionRecommendationReason.HOLIDAY
                        else -> OccasionRecommendationReason.DISCOVER
                    },
                    isPlanned(row.occasion, date, tasks, zone),
                    minOf(row.daysUntil.toInt(), if (row.occasion.isEducational) 14 else if (row.isFavorite) 7 else 3),
                )
            }
    }
}
