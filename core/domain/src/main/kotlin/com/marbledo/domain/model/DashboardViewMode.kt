package com.marbledo.domain.model

import kotlinx.serialization.Serializable

/**
 * How the dashboard lays out its task and countdown sections. The same mode drives both sections,
 * so the whole home screen can be scanned the way the user likes it.
 */
@Serializable
enum class DashboardViewMode {
    /** Plain stacked cards, grouped by date. */
    LIST,

    /** A vertical line with a node per task, or a horizontal road across the countdowns. */
    TIMELINE,

    /** Compact cards packed two per row. */
    GRID,
}
