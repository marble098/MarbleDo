package com.marbledo.domain.model

/**
 * Countdown looks. The enum lives in the domain layer so both the tasks feature (which lets a task
 * opt into a countdown) and the countdown feature (which renders it) can share the same ids.
 *
 * Ids are persisted, so they must stay stable. [from] also understands the ids of the first
 * generation of themes that shipped before the gallery redesign.
 */
enum class CountdownTheme(val id: String) {
    MARBLE_ORB("MARBLE_ORB"),
    AURORA_VEIL("AURORA_VEIL"),
    NEON_GRID("NEON_GRID"),
    FLIP_STUDIO("FLIP_STUDIO"),
    GLASS_LAYERS("GLASS_LAYERS"),
    ORBITAL_RINGS("ORBITAL_RINGS"),
    TERMINAL_MATRIX("TERMINAL_MATRIX"),
    LIQUID_TIDE("LIQUID_TIDE"),
    PAPER_TYPE("PAPER_TYPE"),
    STELLAR_NIGHT("STELLAR_NIGHT"),
    MOMENT_BLOCKS("MOMENT_BLOCKS"),
    DIAL_GAUGE("DIAL_GAUGE"),
    ;

    companion object {
        private val legacyIds = mapOf(
            "MINIMAL" to PAPER_TYPE,
            "GLASS" to GLASS_LAYERS,
            "NEON" to NEON_GRID,
            "FLIP_CLOCK" to FLIP_STUDIO,
            "CIRCULAR" to ORBITAL_RINGS,
            "LIQUID" to LIQUID_TIDE,
            "RING" to ORBITAL_RINGS,
            "ANALOG" to MOMENT_BLOCKS,
            "TERMINAL" to TERMINAL_MATRIX,
            "MARBLE" to MARBLE_ORB,
            "AURORA" to AURORA_VEIL,
            "RETRO" to DIAL_GAUGE,
        )

        val DEFAULT = MARBLE_ORB

        fun from(id: String): CountdownTheme = entries.firstOrNull { it.id == id } ?: legacyIds[id] ?: DEFAULT

        fun isKnown(id: String): Boolean = entries.any { it.id == id } || legacyIds.containsKey(id)
    }
}
