package com.marbledo.feature.calendar

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.OccasionCulture

/** The same gate is used by UI, notifications and widgets; hidden content cannot leak elsewhere. */
fun AppSettings.enabledOccasionCategories(): Set<OccasionCategory> = buildSet {
    if (officialEventsEnabled) add(OccasionCategory.OFFICIAL)
    if (nationalEventsEnabled) add(OccasionCategory.NATIONAL)
    if (religiousEventsEnabled) add(OccasionCategory.RELIGIOUS)
    if (personalEventsEnabled) add(OccasionCategory.PERSONAL)
    if (internationalEventsEnabled) add(OccasionCategory.INTERNATIONAL)
    if (educationModeEnabled) add(OccasionCategory.EDUCATIONAL)
}

/** Reject stale CDN copies, unsupported schemas and suspiciously truncated catalogs. */
object CatalogUpdatePolicy {
    fun accepts(candidate: OccasionCatalog, baseline: OccasionCatalog): Boolean {
        if (candidate.schemaVersion !in 1..OccasionCatalog.SUPPORTED_SCHEMA) return false
        if (candidate.occasions.size !in 20..5_000) return false
        val oldVersion = baseline.dataVersion?.toLongOrNull()
        val newVersion = candidate.dataVersion?.toLongOrNull()
        if (oldVersion != null && (newVersion == null || newVersion < oldVersion)) return false
        if (candidate.schemaVersion < baseline.schemaVersion) return false
        if (candidate.occasions.size * 100 < baseline.occasions.size * 85) return false
        val requiredCategories = baseline.occasions.map { it.category }.toSet()
        if (!candidate.occasions.map { it.category }.toSet().containsAll(requiredCategories)) return false
        val requiredCultures = baseline.occasions.flatMap { it.cultures }.toSet()
        if (!candidate.occasions.flatMap { it.cultures }.toSet().containsAll(requiredCultures)) return false
        if (baseline.schoolPrograms.isNotEmpty() && candidate.schoolPrograms.isEmpty()) return false
        return true
    }
}

/** One pure eligibility gate, including replacement precedence, shared by every resolved index. */
object OccasionVisibility {
    fun forYear(catalog: OccasionCatalog, year: Int, categories: Set<OccasionCategory>, cultures: Set<OccasionCulture>): List<Occasion> {
        val override = catalog.yearly[year]
        val replacementIds = override?.occasions.orEmpty().map { it.id }.toSet()
        return (catalog.occasions.filter { it.titleEn !in override?.removedTitlesEn.orEmpty() && it.id !in replacementIds } + override?.occasions.orEmpty())
            .filter { it.category in categories && it.cultures.any(cultures::contains) }.distinctBy { it.id }
    }
}
