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
 * The redesign removed rows rather than decorating them. The old card stacked a mood pill, a
 * title, two secondary dates, a progress label, a bar, a labelled next-task block and a labelled
 * occasion block — six labels for four facts, which is what read as "busy". Now:
 *
 *  * the header is a date and a mood word (the secondary calendars only appear when expanded);
 *  * progress is one reading with the bar beside it, not above it;
 *  * the next step is the single filled surface, with its label and time on one line;
 *  * occasions are a value line, not a section with its own heading.
 *
 * The banner look keeps its mood rule: it is already tinted for the day (a drawable per mood)
 * so that it stays a first-class `setImageViewResource()` call — a reflective action that a
 * locked-down SystemUI rejected would abort the whole apply and take the row, and the
 * status-bar icon with it, off the screen.
 *
 * **Colour is decided in one place.** A custom `RemoteViews` card is drawn inside SystemUI's
 * shade, which follows the wallpaper or the device theme, so the card paints its own plate and
 * states every text colour on top of it. The plate and the text come from the same
 * [NotificationPalette] — derived from the app theme and checked with [MarbleContrast]. That is
 * what fixes the two things users saw: parts of the card keeping the old indigo after a theme
 * switch, and muted text that disappeared on one of the two card plates.
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
        return RemoteViews(context.packageName, layout(design, expanded, rtl)).apply {
            // The plate first. Every colour below was checked against this fill, so painting it is
            // what makes the rest of the actions safe; the old card left the text on whatever
            // SystemUI's shade happened to show behind a transparent root.
            setImageViewResource(R.id.notification_card_plate, plateRes(colors.isDark))
            // Text, stated explicitly: a RemoteViews TextView inherits SystemUI's colour, which is
            // invisible on one of the two plates. The compact and expanded cards do not declare
            // the same views, and setting a colour on an id a layout does not have makes the
            // whole apply throw, so the two states are written separately.
            setTextColor(R.id.notification_title, colors.text.toInt())
            setTextViewText(R.id.notification_mood_pill, content.moodLabel)
            if (!expanded) {
                setTextColor(R.id.notification_compact_summary, colors.muted.toInt())
            } else {
                listOf(
                    R.id.notification_secondary_dates,
                    R.id.notification_progress_label,
                    R.id.notification_next_time,
                    R.id.notification_occasions_text,
                ).forEach { setTextColor(it, colors.muted.toInt()) }
                setTextColor(R.id.notification_next_title, colors.text.toInt())
            }
            // The accent labels: the mood word in the header, and the next-step label on the
            // expanded card. These are exactly the views the layouts declare with
            // `@color/notification_card_accent`; the contract tests assert that id-for-id.
            //
            // The tint is applied unconditionally. Leaving it to the layout resource means falling
            // back to `values-night`, i.e. to the *device* theme — a light-theme indigo on a dark
            // card whenever the app theme and the device disagree, which is the bug this whole
            // palette exists to remove.
            if (!expanded) {
                listOf(R.id.notification_mood_pill).forEach { setTextColor(it, tint) }
            } else {
                listOf(
                    R.id.notification_mood_pill,
                    R.id.notification_next_label,
                ).forEach { setTextColor(it, tint) }
            }
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
            setContentDescription(
                R.id.notification_root,
                if (expanded) content.expandedText else "${content.title}. ${content.summaryLine}",
            )
            if (!expanded) {
                setTextViewText(R.id.notification_compact_summary, content.summaryLine)
            } else {
                setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)

                setViewVisibility(R.id.notification_progress_container,
                    if (content.hasProgress) View.VISIBLE else View.GONE)
                setTextViewText(R.id.notification_progress_label, content.progressLabel)
                // The bar only makes sense when the day has a total; an overdue-only reading is a
                // sentence in the same row, so the track is hidden rather than drawn empty. The
                // bar itself is a themed bitmap: a rounded track and fill in the app theme accent.
                setViewVisibility(R.id.notification_progress, if (content.todayTotal > 0) View.VISIBLE else View.GONE)
                if (content.todayTotal > 0) {
                    NotificationArtwork.progressBitmap(
                        context,
                        content.todayCompleted,
                        content.todayTotal,
                        colors.accent.toInt(),
                        colors.isDark,
                    )?.let { bar -> setImageViewBitmap(R.id.notification_progress, bar) }
                }

                val showNextSection = content.hasNextTask || !content.hasOccasions
                setViewVisibility(R.id.notification_next_container, if (showNextSection) View.VISIBLE else View.GONE)
                // Minimal is text only: its plate stays hidden and the drawable behind the
                // container is already transparent. Every other design paints the plate in the
                // theme accent.
                if (design == "minimal") {
                    setViewVisibility(R.id.notification_focus_plate, View.GONE)
                }
                if (design != "minimal") {
                    NotificationArtwork.focusPlate(context, colors.accent.toInt(), colors.isDark)?.let { plate ->
                        setImageViewBitmap(R.id.notification_focus_plate, plate)
                    }
                }
                setTextViewText(R.id.notification_next_label, content.nextTaskLabel)
                setTextViewText(R.id.notification_next_title, content.nextTaskTitle ?: content.fallbackTaskText)
                setTextViewText(R.id.notification_next_time, content.nextTaskTime.orEmpty())
                setViewVisibility(R.id.notification_next_time, if (content.nextTaskTime == null) View.GONE else View.VISIBLE)

                setViewVisibility(R.id.notification_occasions_container, if (content.hasOccasions) View.VISIBLE else View.GONE)
                setTextViewText(R.id.notification_occasions_text, content.occasionsLine)
            }
        }
    }
}
