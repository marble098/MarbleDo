package com.marbledo.domain.model

import kotlinx.serialization.Serializable

/**
 * How the day number is drawn inside the persistent status-bar date icon.
 *
 * The status bar renders a small icon inside a narrow strip, so the day number has to fight for
 * every pixel it gets. These styles trade context (a calendar page, a mood glyph) against raw
 * digit size, and the user can pick the one that stays readable on their device.
 */
@Serializable
enum class StatusBarIconStyle {
    /**
     * Default: the day number fills almost the whole icon with heavy strokes, and the mood is a
     * small mark tucked into the top corner so it never steals space from the digits.
     */
    BOLD_DIGITS,

    /** Classic look: a thin calendar page with rings and the mood glyph above the number. */
    CALENDAR_PAGE,

    /** No generated bitmap: the plain vector calendar icon, with no day number at all. */
    ICON_ONLY,

    ;

    companion object {
        val DEFAULT = BOLD_DIGITS

        fun from(id: String): StatusBarIconStyle = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
