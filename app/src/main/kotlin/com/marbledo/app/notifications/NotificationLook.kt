package com.marble098.marbledo.notifications

import com.marble098.marbledo.R
import com.marbledo.core.designsystem.MarbleContrast

/**
 * The four looks of the ongoing notification, and the anatomy each one has.
 *
 * `scripts/generate_notification_layouts.py` writes the sixteen layouts from the same table, and
 * `scripts/tests/test_notification_designs.py` checks that the flags here match the ids that each
 * generated layout declares. The flags are what let [CalendarNotificationViews] stay honest: a
 * `RemoteViews` action on an id the layout does not have aborts the whole apply and takes the row —
 * status-bar icon included — off the screen, so the builder only touches what the look owns.
 *
 * The looks differ in *anatomy*, not in a couple of dp:
 *
 *  * [CLASSIC] — a day badge, a tonal state pill and the tasks on one tonal panel;
 *  * [MINIMAL] — text only: a coloured dot and a word for the state, no badge, no panel;
 *  * [EDITORIAL] — the date set large as a masthead over a hairline, the task as a pull-quote
 *    beside a vertical accent rule;
 *  * [BANNER] — a full-width accent band carrying the date and the state, the task below it.
 */
enum class NotificationLook(
    val key: String,
    /** The date badge leads the header. */
    val badge: Boolean,
    /** The state sits on a tonal pill; otherwise it is a dot and a word. */
    val chipPill: Boolean,
    /** The tasks sit on a tonal panel. */
    val taskPanel: Boolean,
    /** A vertical accent rule leads the task block. */
    val accentRule: Boolean,
    /** The header is a full-width band in the theme accent. */
    val band: Boolean,
    /** A hairline closes the header. */
    val headerDivider: Boolean,
) {
    CLASSIC("classic", badge = true, chipPill = true, taskPanel = true, accentRule = false, band = false, headerDivider = false),
    MINIMAL("minimal", badge = false, chipPill = false, taskPanel = false, accentRule = false, band = false, headerDivider = false),
    EDITORIAL("editorial", badge = false, chipPill = false, taskPanel = false, accentRule = true, band = false, headerDivider = true),
    BANNER("banner", badge = false, chipPill = false, taskPanel = false, accentRule = false, band = true, headerDivider = false),
    ;

    companion object {
        /** The look a Settings value names; anything unknown is the documented fallback, classic. */
        fun from(key: String?): NotificationLook = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/**
 * The fills of the state pill, one per state and plate lightness.
 *
 * They are shape drawables rather than bitmaps so the pill scales to any word without its corners
 * being stretched, and they are literal colours because the card follows the app theme rather than
 * the device's night mode. The same values are repeated here so the builder can choose the ink
 * *against the fill the pill actually paints*; `test_notification_designs.py` keeps the two in step.
 */
internal object NotificationChipFills {
    const val OVERDUE_LIGHT = 0xFFF3D9D3L
    const val OVERDUE_DARK = 0xFF4E2B26L
    const val OCCASION_LIGHT = 0xFFE2DAF1L
    const val OCCASION_DARK = 0xFF3A3252L
    const val NEUTRAL_LIGHT = 0xFFE4E3E9L
    const val NEUTRAL_DARK = 0xFF35363EL

    fun fill(state: NotificationState, dark: Boolean): Long = when (state) {
        NotificationState.OVERDUE -> if (dark) OVERDUE_DARK else OVERDUE_LIGHT
        NotificationState.OCCASION -> if (dark) OCCASION_DARK else OCCASION_LIGHT
        NotificationState.TODAY, NotificationState.QUIET -> if (dark) NEUTRAL_DARK else NEUTRAL_LIGHT
    }

    fun drawable(state: NotificationState, dark: Boolean): Int = when (state) {
        NotificationState.OVERDUE -> if (dark) R.drawable.notification_chip_overdue_dark else R.drawable.notification_chip_overdue_light
        NotificationState.OCCASION -> if (dark) R.drawable.notification_chip_occasion_dark else R.drawable.notification_chip_occasion_light
        NotificationState.TODAY, NotificationState.QUIET -> if (dark) R.drawable.notification_chip_neutral_dark else R.drawable.notification_chip_neutral_light
    }

    /** The state's hue, moved in lightness only until it reads as text on its own pill. */
    fun ink(palette: NotificationPalette, state: NotificationState): Int =
        MarbleContrast.readable(palette.stateHue(state), fill(state, palette.isDark), MarbleContrast.TEXT_MINIMUM).toInt()
}

/** The hue a state is painted in on the card plate: warning red, the brand accent, calm purple. */
internal fun NotificationPalette.stateHue(state: NotificationState): Long = when (state) {
    NotificationState.OVERDUE -> overdue
    NotificationState.TODAY -> today
    NotificationState.OCCASION -> occasion
    NotificationState.QUIET -> muted
}
