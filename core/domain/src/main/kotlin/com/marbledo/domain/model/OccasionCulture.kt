package com.marbledo.domain.model

/** Stable, independently selectable discovery packs; these never define local public holidays. */
enum class OccasionCulture(val key: String) {
    IRANIAN("iranian"), GLOBAL("global"), WESTERN("western"),
    NORTH_AMERICA("north_america"), EUROPE("europe"), OCEANIA("oceania");

    companion object {
        val all: Set<OccasionCulture> = entries.toSet()
        val defaultIds: List<String> = entries.map { it.key }
        fun fromKey(key: String): OccasionCulture? = entries.firstOrNull { it.key == key }
    }
}

fun AppSettings.enabledOccasionCultures(): Set<OccasionCulture> =
    occasionCultureIds.mapNotNull(OccasionCulture::fromKey).toSet()
