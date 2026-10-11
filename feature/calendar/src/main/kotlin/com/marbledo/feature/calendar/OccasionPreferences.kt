package com.marbledo.feature.calendar

import com.marbledo.domain.model.AppSettings

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
        if (baseline.schoolPrograms.isNotEmpty() && candidate.schoolPrograms.isEmpty()) return false
        return true
    }
}
