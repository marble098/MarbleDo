package com.marbledo.domain.model

/**
 * Palettes for the habit tracker's progress screens. The ids are persisted with every habit, so
 * they must stay stable; [from] keeps unknown ids on the default look instead of crashing.
 */
enum class HabitTheme(val id: String) {
    /** Violet-to-teal aurora on a deep night background. */
    AURORA("AURORA"),

    /** Warm embers: the "streak on fire" palette. */
    EMBER("EMBER"),

    /** Soft mint garden, calm and slow-growing. */
    GARDEN("GARDEN"),

    /** Midnight blue with a cold silver ring. */
    MIDNIGHT("MIDNIGHT"),

    /** Sakura pinks for gentle, self-compassionate tracking. */
    BLOOM("BLOOM"),

    /** Frosted glass: neutral, high contrast, data first. */
    GLASS("GLASS"),
    ;

    companion object {
        val DEFAULT = AURORA

        fun from(id: String): HabitTheme = entries.firstOrNull { it.id == id } ?: DEFAULT

        fun isKnown(id: String): Boolean = entries.any { it.id == id }
    }
}
