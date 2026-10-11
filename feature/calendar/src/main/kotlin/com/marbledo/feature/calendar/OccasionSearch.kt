package com.marbledo.feature.calendar

import com.marbledo.domain.util.TextNormalizer

data class OccasionFilter(
    val query: String = "",
    val category: OccasionCategory? = null,
    val calendar: OccasionCalendar? = null,
    val holidaysOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val educationOnly: Boolean = false,
    val culturalOnly: Boolean = false,
)

object OccasionSearch {
    /** Search both languages and tags regardless of UI locale; AND-match query tokens. */
    fun filter(rows: List<DatedOccasion>, filter: OccasionFilter, favoriteIds: Set<String>): List<DatedOccasion> {
        val tokens = searchKey(filter.query).split(' ').filter { it.isNotBlank() }
        return rows.filter { row ->
            val occasion = row.occasion
            val text = searchKey("${occasion.titleFa} ${occasion.titleEn} ${occasion.tags.joinToString(" ")}")
            (filter.category == null || occasion.category == filter.category) &&
                (filter.calendar == null || occasion.calendar == filter.calendar) &&
                (!filter.holidaysOnly || occasion.isHoliday) &&
                (!filter.favoritesOnly || occasion.id in favoriteIds) &&
                (!filter.educationOnly || occasion.isEducational) &&
                (!filter.culturalOnly || "culture" in occasion.tags) && tokens.all { it in text }
        }.sortedWith(compareBy({ it.year }, { it.month }, { it.day }, { it.occasion.titleFa }))
    }

    private fun searchKey(value: String): String = TextNormalizer.searchKey(TextNormalizer.digitsToLatin(value))
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")

    fun academicYear(index: OccasionIndex, startYear: Int): List<DatedOccasion> =
        index.forYear(startYear).filter { it.month >= 7 } + index.forYear(startYear + 1).filter { it.month < 7 }
}
