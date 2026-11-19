package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.marble098.marbledo.R
import com.marbledo.core.designsystem.MarbleContrast
import com.marbledo.domain.model.AppThemeMode

/**
 * The one place that turns [PersistentCalendarNotification.NotificationBoxContent] into the
 * framework-decorated rows of the ongoing notification.
 *
 * The redesign separates the two things the old card had merged into one stack: the **task** the
 * user has to act on (priority 1) and the **occasions** that are merely interesting (priority 2).
 * Concretely, and in the order they appear:
 *
 *  * the header is the Jalali date plus a **state chip** — "۳ عقب‌افتاده" in warning red or "امروز"
 *    in the brand accent — instead of a progress bar nothing could be read from;
 *  * the task block gives the title **two lines** in the heads-up card and four when expanded, so
 *    a task is never reduced to a word and an ellipsis;
 *  * the occasion block is second, under a hairline, with a star of its own, and is hidden
 *    entirely when there is nothing to say;
 *  * Gregorian and lunar dates only appear on the expanded card, where there is room for them.
 *
 * **Colour is decided in one place.** A custom `RemoteViews` card is drawn inside SystemUI's
 * shade, which follows the wallpaper or the device theme, so the card paints its own plate and
 * states every text colour on top of it. The plate, the text and the three semantic states all
 * come from the same [NotificationPalette] — derived from the app theme and checked with
 * [MarbleContrast] — which is what stops "overdue" from rendering as a brown that "occasion"
 * also happens to use.
 *
 * The banner look keeps its mood rule: it is already tinted for the day (a drawable per mood)
 * so that it stays a first-class `setImageViewResource()` call — a reflective action that a
 * locked-down SystemUI rejected would abort the whole apply and take the row, and the
 * status-bar icon with it, off the screen.
 */
internal object CalendarNotificationViews {

    /** A drawable per mood: no reflection, and the rule keeps its rounded corner. */
    private fun accentRuleRes(mood: NotificationMood): Int = when (mood) {
        NotificationMood.HOLIDAY -> R.drawable.notification_banner_bar_holiday
        NotificationMood.RELIGIOUS -> R.drawable.notification_banner_bar_religious
        NotificationMood.OCCASION -> R.drawable.notification_banner_bar_occasion
        NotificationMood.TASKS_DUE -> R.drawable.notification_banner_bar_tasks
        NotificationMood.QUIET -> R.drawable.notification_banner_bar_quiet
    }

    /** The card plate for the app theme's lightness, not the device's. */
    private fun plateRes(isDark: Boolean): Int =
        if (isDark) R.drawable.notification_card_plate_dark else R.drawable.notification_card_plate_light

    private fun layout(design: String, expanded: Boolean, rtl: Boolean): Int = when (design) {
        "minimal" -> if (expanded) {
            if (rtl) R.layout.notification_minimal_expanded_rtl else R.layout.notification_minimal_expanded_ltr
        } else {
            if (rtl) R.layout.notification_minimal_compact_rtl else R.layout.notification_minimal_compact_ltr
        }
        "editorial" -> if (expanded) {
            if (rtl) R.layout.notification_editorial_expanded_rtl else R.layout.notification_editorial_expanded_ltr
        } else {
            if (rtl) R.layout.notification_editorial_compact_rtl else R.layout.notification_editorial_compact_ltr
        }
        "banner" -> if (expanded) {
            if (rtl) R.layout.notification_banner_expanded_rtl else R.layout.notification_banner_expanded_ltr
        } else {
            if (rtl) R.layout.notification_banner_compact_rtl else R.layout.notification_banner_compact_ltr
        }
        else -> if (expanded) {
            if (rtl) R.layout.notification_classic_expanded_rtl else R.layout.notification_classic_expanded_ltr
        } else {
            if (rtl) R.layout.notification_classic_compact_rtl else R.layout.notification_classic_compact_ltr
        }
    }

    /**
     * The chip: one pill, the state word and a mark whose meaning matches the state.
     *
     * The pill is repainted as a bitmap in the state's own colour, with a border in the state's
     * hue, and the mark is a tinted bitmap in the chip's ink. Both are first-class RemoteViews
     * actions; a `setInt("setBackgroundColor")` or a `setInt("setColorFilter", …)` on the label
     * would be reflective calls that can abort the whole row.
     */
    private fun chipColors(palette: NotificationPalette, state: NotificationState): Pair<Int, Int> = when (state) {
        NotificationState.OVERDUE -> palette.overdue.toInt() to palette.overdueSoft.toInt()
        NotificationState.TODAY -> palette.accent.toInt() to palette.accentFill.toInt()
        NotificationState.OCCASION -> palette.occasion.toInt() to palette.occasionFill.toInt()
        NotificationState.QUIET -> palette.muted.toInt() to palette.accentFill.toInt()
    }

    /**
     * The ink of the chip's word *on the fill behind it*.
     *
     * [chipColors]' first value is the state's hue, chosen to read on the *card*; it is not
     * necessarily readable on the state's own soft fill, and on the light theme's red it is not
     * (1.3:1 — the word would have looked embossed). The fill is the background, the hue is only
     * used for the border, so the word and the mark both take the ink that wins on the fill.
     */
    private fun inkOn(fill: Int): Int = MarbleContrast.bestInk(fill)

    private fun chipIcon(state: NotificationState): Int = when (state) {
        NotificationState.OVERDUE -> R.drawable.ic_notification_warning
        NotificationState.TODAY -> R.drawable.ic_stat_calendar_tasks
        NotificationState.OCCASION -> R.drawable.ic_notification_occasion_star
        NotificationState.QUIET -> R.drawable.ic_stat_calendar
    }

    fun create(
        context: Context,
        content: PersistentCalendarNotification.NotificationBoxContent,
        badge: Bitmap?,
        expanded: Boolean,
        design: String = "classic",
        rtl: Boolean = false,
        accentColor: Int = 0,
        palette: NotificationPalette? = null,
    ): RemoteViews {
        val colors = palette ?: notificationPalette(
            context,
            AppThemeMode.AUTO,
            dark = systemIsDark(context),
        )
        val tint = if (accentColor != 0) accentColor else colors.accent.toInt()
        val state = content.state
        val (stateHue, chipFill) = chipColors(colors, state)
        val chipInk = inkOn(chipFill)
        return RemoteViews(context.packageName, layout(design, expanded, rtl)).apply {
            // The plate first. Every colour below was checked against this fill, so painting it is
            // what makes the rest of the actions safe; the old card left the text on whatever
            // SystemUI's shade happened to show behind a transparent root.
            setImageViewResource(R.id.notification_card_plate, plateRes(colors.isDark))
            // Text, stated explicitly: a RemoteViews TextView inherits SystemUI's colour, which is
            // invisible on one of the two plates.
            setTextColor(R.id.notification_title, colors.text.toInt())
            setTextViewText(R.id.notification_title, content.title)
            setTextColor(R.id.notification_task_title, colors.text.toInt())
            setTextColor(R.id.notification_task_meta, colors.muted.toInt())
            setTextViewText(R.id.notification_task_title, content.taskTitle ?: content.emptyTaskText)
            setTextViewText(R.id.notification_task_meta, content.taskMetaLine.orEmpty())

            // The state chip: the word and its mark carry the same meaning, and the pill is painted
            // in the state's colour. This is the badge the progress bar was replaced with.
            setTextViewText(R.id.notification_state_label, content.stateLabel)
            setTextColor(R.id.notification_state_label, chipInk)
            NotificationArtwork.chipPill(context, chipFill, stateHue, colors.isDark)?.let { pill ->
                setImageViewBitmap(R.id.notification_state_chip_plate, pill)
            }
            // The mark is a white vector in the resources, so it is repainted in the chip's ink
            // rather than left white on a pale fill.
            setTintedIcon(context, R.id.notification_state_icon, chipIcon(state), chipInk)

            // Minimal is deliberately text only: the badge is declared so every layout accepts the
            // same actions, and hidden here instead of shipping a second set of ids.
            if (badge != null && design != "minimal") setImageViewBitmap(R.id.notification_badge, badge)
            else setViewVisibility(R.id.notification_badge, View.GONE)

            // The banner look tints its leading rule with the day's mood colour, through a
            // first-class RemoteViews action on an ImageView. The resource stays as the un-themed
            // fallback; when a theme accent is available the same rule is repainted — still a
            // first-class bitmap call — in the blend of the accent and the mood, so the line
            // follows the app theme.
            if (design == "banner") {
                setImageViewResource(R.id.notification_accent_rule, accentRuleRes(content.mood))
                NotificationArtwork.accentRule(context, tint, content.mood, colors.isDark)?.let { rule ->
                    setImageViewBitmap(R.id.notification_accent_rule, rule)
                }
            }
            setContentDescription(R.id.notification_root, content.accessibilityText(expanded))

            if (expanded) {
                setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)
                setTextColor(R.id.notification_secondary_dates, colors.muted.toInt())

                // The task block lives on the one filled surface of the card. Minimal is text only:
                // its plate stays hidden and the drawable behind the container is transparent.
                if (design == "minimal") {
                    setViewVisibility(R.id.notification_focus_plate, View.GONE)
                } else {
                    NotificationArtwork.focusPlate(context, colors.accent.toInt(), colors.isDark)?.let { plate ->
                        setImageViewBitmap(R.id.notification_focus_plate, plate)
                    }
                }
                setTextColor(R.id.notification_tasks_label, tint)
                setTextViewText(R.id.notification_tasks_label, content.tasksLabel)
                setTextColor(R.id.notification_task_second_title, colors.text.toInt())
                setTextColor(R.id.notification_task_second_meta, colors.muted.toInt())
                setTextViewText(R.id.notification_task_second_title, content.secondTaskTitle.orEmpty())
                setTextViewText(R.id.notification_task_second_meta, content.secondTaskMetaLine.orEmpty())
                setViewVisibility(
                    R.id.notification_task_second_title,
                    if (content.secondTaskTitle == null) View.GONE else View.VISIBLE,
                )
                setViewVisibility(
                    R.id.notification_task_second_meta,
                    if (content.secondTaskMetaLine == null) View.GONE else View.VISIBLE,
                )
                setTextViewText(R.id.notification_task_more, content.moreTasksLine.orEmpty())
                setViewVisibility(R.id.notification_task_more, if (content.moreTasksLine == null) View.GONE else View.VISIBLE)

                // Priority 2: the occasions, under their own hairline and star. An empty day hides
                // the whole block instead of printing "nothing" under a heading.
                setViewVisibility(
                    R.id.notification_occasions_container,
                    if (content.hasOccasions) View.VISIBLE else View.GONE,
                )
                setViewVisibility(R.id.notification_occasions_divider, if (content.occasionsDivider) View.VISIBLE else View.GONE)
                setTextColor(R.id.notification_occasions_label, colors.occasion.toInt())
                setTextColor(R.id.notification_occasions_text, colors.muted.toInt())
                setTextViewText(R.id.notification_occasions_label, content.occasionsLabel)
                setTextViewText(R.id.notification_occasions_text, content.occasionsLine)
                setTintedIcon(context, R.id.notification_occasions_icon, R.drawable.ic_notification_occasion_star,
                    colors.occasion.toInt())
            }
        }
    }

    /**
     * `setInt("setColorFilter", …)` is reflective and can abort the row, so a mark that has to be
     * in a semantic colour is shipped as a pre-tinted bitmap instead — drawn once per colour and
     * cached by [NotificationArtwork.tintedVector].
     *
     * [drawableRes] is a resource, never the view id: passing an id here would resolve to whatever
     * unrelated resource happens to share the number, which is the kind of bug that only shows up
     * as a missing icon on a device.
     */
    private fun RemoteViews.setTintedIcon(context: Context, viewId: Int, drawableRes: Int, color: Int) {
        NotificationArtwork.tintedVector(context, drawableRes, color)?.let { bitmap ->
            setImageViewBitmap(viewId, bitmap)
        }
    }
}

/**
 * The semantic state the chip states, in the order the card prefers to talk about them.
 *
 * Public because it is a field of the public [PersistentCalendarNotification.NotificationBoxContent]:
 * a public data class cannot expose an `internal` type, and the Settings preview reads the state to
 * pick the same colours the shade paints.
 */
enum class NotificationState { OVERDUE, TODAY, OCCASION, QUIET }

/** The mark that belongs to a state, for the header of the Settings preview. */
internal fun NotificationState.iconRes(): Int = when (this) {
    NotificationState.OVERDUE -> R.drawable.ic_notification_warning
    NotificationState.TODAY -> R.drawable.ic_stat_calendar_tasks
    NotificationState.OCCASION -> R.drawable.ic_notification_occasion_star
    NotificationState.QUIET -> R.drawable.ic_stat_calendar
}
