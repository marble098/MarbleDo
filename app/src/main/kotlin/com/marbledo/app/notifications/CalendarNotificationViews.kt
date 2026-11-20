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
 * The card has two sections in priority order — the **task** the user has to act on, then the
 * **occasions** that are merely interesting, hidden whole on an empty day — and a header with the
 * Jalali date and a **state** ("۳ عقب‌افتاده" / "امروز"). Which elements dress that skeleton is
 * decided by the [NotificationLook] the user picked, and this builder only ever touches the ids
 * that look declares: a `RemoteViews` action on a missing id aborts the whole apply and takes the
 * row, status-bar icon included, off the screen.
 *
 * **Colour is decided in one place.** A custom `RemoteViews` card is drawn inside SystemUI's
 * shade, which follows the wallpaper or the device theme, so the card paints its own plate and
 * states every text colour on top of it, all from one [NotificationPalette] derived from the app
 * theme and checked with [MarbleContrast].
 *
 * **Only first-class actions.** Every colour is applied with `setTextColor`, `setImageViewResource`
 * or `setImageViewBitmap`; the reflective `setInt("setBackgroundColor")` / `setColorFilter` calls
 * that a locked-down SystemUI rejects are never used. Shapes that have to scale (plate, panel, pill,
 * hairlines) are shape drawables with a light and a dark twin; bitmaps are reserved for the few
 * shapes that carry the theme accent and survive stretching.
 */
internal object CalendarNotificationViews {

    private fun layout(look: NotificationLook, expanded: Boolean, rtl: Boolean): Int = when (look) {
        NotificationLook.MINIMAL -> if (expanded) {
            if (rtl) R.layout.notification_minimal_expanded_rtl else R.layout.notification_minimal_expanded_ltr
        } else {
            if (rtl) R.layout.notification_minimal_compact_rtl else R.layout.notification_minimal_compact_ltr
        }
        NotificationLook.EDITORIAL -> if (expanded) {
            if (rtl) R.layout.notification_editorial_expanded_rtl else R.layout.notification_editorial_expanded_ltr
        } else {
            if (rtl) R.layout.notification_editorial_compact_rtl else R.layout.notification_editorial_compact_ltr
        }
        NotificationLook.BANNER -> if (expanded) {
            if (rtl) R.layout.notification_banner_expanded_rtl else R.layout.notification_banner_expanded_ltr
        } else {
            if (rtl) R.layout.notification_banner_compact_rtl else R.layout.notification_banner_compact_ltr
        }
        NotificationLook.CLASSIC -> if (expanded) {
            if (rtl) R.layout.notification_classic_expanded_rtl else R.layout.notification_classic_expanded_ltr
        } else {
            if (rtl) R.layout.notification_classic_compact_rtl else R.layout.notification_classic_compact_ltr
        }
    }

    private fun plateRes(dark: Boolean): Int =
        if (dark) R.drawable.notification_card_plate_dark else R.drawable.notification_card_plate_light

    private fun panelRes(dark: Boolean): Int =
        if (dark) R.drawable.notification_panel_plate_dark else R.drawable.notification_panel_plate_light

    private fun hairlineRes(dark: Boolean): Int =
        if (dark) R.drawable.notification_hairline_dark else R.drawable.notification_hairline_light

    private fun stateMark(state: NotificationState): Int = when (state) {
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
        design: String = NotificationLook.CLASSIC.key,
        rtl: Boolean = false,
        accentColor: Int = 0,
        palette: NotificationPalette? = null,
    ): RemoteViews {
        val look = NotificationLook.from(design)
        val colors = palette ?: notificationPalette(context, AppThemeMode.AUTO, dark = systemIsDark(context))
        val accent = if (accentColor != 0) accentColor else colors.accent.toInt()
        val state = content.state
        val stateHue = colors.stateHue(state).toInt()
        val onAccent = colors.onAccent.toInt()

        // The header's own ink: the plate's text, or the band's ink when the header is a band.
        val headerInk = if (look.band) onAccent else colors.text.toInt()
        // The state's word and mark. On a pill both take the state hue re-checked against the
        // pill's fill; as a dot-and-word they take the hue checked against the plate; on the band
        // the word is the band's ink and only the dot keeps the hue (lifted until it reads there).
        val stateInk = when {
            look.chipPill -> NotificationChipFills.ink(colors, state)
            look.band -> onAccent
            else -> stateHue
        }
        val stateMarkColor = when {
            look.chipPill -> stateInk
            look.band -> MarbleContrast.readable(stateHue, accent, MarbleContrast.UI_MINIMUM)
            else -> stateHue
        }

        return RemoteViews(context.packageName, layout(look, expanded, rtl)).apply {
            // The plate first. Every colour below was checked against this fill.
            setImageViewResource(R.id.notification_card_plate, plateRes(colors.isDark))
            setContentDescription(R.id.notification_root, content.accessibilityText(expanded))

            // ---- header: date, state ------------------------------------------------------
            setTextViewText(R.id.notification_title, content.title)
            setTextColor(R.id.notification_title, headerInk)
            setTextViewText(R.id.notification_state_label, content.stateLabel)
            setTextColor(R.id.notification_state_label, stateInk)
            setTintedIcon(
                context, R.id.notification_state_icon,
                if (look.chipPill) stateMark(state) else R.drawable.ic_notification_state_dot,
                stateMarkColor,
            )
            if (look.chipPill) {
                setImageViewResource(R.id.notification_state_chip_plate, NotificationChipFills.drawable(state, colors.isDark))
            }
            if (look.badge) {
                if (badge != null) setImageViewBitmap(R.id.notification_badge, badge)
                else setViewVisibility(R.id.notification_badge, View.GONE)
            }
            if (look.band) {
                // Three slices: the start and end caps are never scaled, the fill is flat. Which
                // cap is on the left depends on the layout direction, not on the reading order.
                NotificationArtwork.bandCap(context, accent, leading = !rtl)?.let { setImageViewBitmap(R.id.notification_band_start, it) }
                NotificationArtwork.bandFill(context, accent)?.let { setImageViewBitmap(R.id.notification_band_fill, it) }
                NotificationArtwork.bandCap(context, accent, leading = rtl)?.let { setImageViewBitmap(R.id.notification_band_end, it) }
            }
            if (look.headerDivider) {
                setImageViewResource(R.id.notification_header_divider, hairlineRes(colors.isDark))
            }

            // ---- priority 1: the tasks ------------------------------------------------------
            setTextViewText(R.id.notification_task_title, content.taskTitle ?: content.emptyTaskText)
            setTextColor(R.id.notification_task_title, colors.text.toInt())
            setTextViewText(R.id.notification_task_meta, content.taskMetaLine.orEmpty())
            setTextColor(R.id.notification_task_meta, colors.muted.toInt())
            setViewVisibility(R.id.notification_task_meta, if (content.taskMetaLine == null) View.GONE else View.VISIBLE)
            if (look.taskPanel) {
                setImageViewResource(R.id.notification_task_panel, panelRes(colors.isDark))
            }
            if (look.accentRule) {
                NotificationArtwork.verticalRule(context, accent)?.let { setImageViewBitmap(R.id.notification_accent_rule, it) }
            }

            if (!expanded) return@apply

            setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)
            // The banner's cross-check dates sit BELOW the accent band, on the card plate.
            // Using the band's onAccent ink here can turn them white on a light card.
            setTextColor(R.id.notification_secondary_dates, colors.muted.toInt())

            setViewVisibility(R.id.notification_task_second_row, if (content.secondTaskTitle == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.notification_tasks_label, content.tasksLabel)
            setTextColor(R.id.notification_tasks_label, accent)
            setViewVisibility(R.id.notification_tasks_label, if (content.hasTask) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_task_second_title, content.secondTaskTitle.orEmpty())
            setTextColor(R.id.notification_task_second_title, colors.text.toInt())
            setViewVisibility(R.id.notification_task_second_title, if (content.secondTaskTitle == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.notification_task_second_meta, content.secondTaskMetaLine.orEmpty())
            setTextColor(R.id.notification_task_second_meta, colors.muted.toInt())
            setViewVisibility(R.id.notification_task_second_meta, if (content.secondTaskMetaLine == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.notification_task_more, content.moreTasksLine.orEmpty())
            setTextColor(R.id.notification_task_more, colors.muted.toInt())
            setViewVisibility(R.id.notification_task_more, if (content.moreTasksLine == null) View.GONE else View.VISIBLE)

            // ---- priority 2: the occasions, hidden whole on an empty day ----------------------
            setViewVisibility(R.id.notification_occasions_container, if (content.hasOccasions) View.VISIBLE else View.GONE)
            setImageViewResource(R.id.notification_occasions_divider, hairlineRes(colors.isDark))
            setViewVisibility(R.id.notification_occasions_divider, if (content.occasionsDivider) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_occasions_label, content.occasionsLabel)
            setTextColor(R.id.notification_occasions_label, colors.occasion.toInt())
            setTextViewText(R.id.notification_occasions_text, content.occasionsLine)
            setTextColor(R.id.notification_occasions_text, colors.muted.toInt())
            setTintedIcon(context, R.id.notification_occasions_icon, R.drawable.ic_notification_occasion_star, colors.occasion.toInt())
        }
    }

    /**
     * `setInt("setColorFilter", …)` is reflective and can abort the row, so a mark that has to be
     * in a semantic colour is shipped as a pre-tinted bitmap instead — drawn once per colour and
     * cached by [NotificationArtwork.tintedVector].
     */
    private fun RemoteViews.setTintedIcon(context: Context, viewId: Int, drawableRes: Int, color: Int) {
        NotificationArtwork.tintedVector(context, drawableRes, color)?.let { bitmap ->
            setImageViewBitmap(viewId, bitmap)
        }
    }
}

/**
 * The semantic state the header states, in the order the card prefers to talk about them.
 *
 * Public because it is a field of the public [PersistentCalendarNotification.NotificationBoxContent]:
 * a public data class cannot expose an `internal` type, and the Settings preview reads the state to
 * pick the same colours the shade paints.
 */
enum class NotificationState { OVERDUE, TODAY, OCCASION, QUIET }

/** The mark that belongs to a state, for the pill of the classic look and the Settings preview. */
internal fun NotificationState.iconRes(): Int = when (this) {
    NotificationState.OVERDUE -> R.drawable.ic_notification_warning
    NotificationState.TODAY -> R.drawable.ic_stat_calendar_tasks
    NotificationState.OCCASION -> R.drawable.ic_notification_occasion_star
    NotificationState.QUIET -> R.drawable.ic_stat_calendar
}
