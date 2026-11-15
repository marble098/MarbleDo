package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.marble098.marbledo.R

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
    ): RemoteViews = RemoteViews(context.packageName,
        layout(design, expanded, rtl)).apply {
        setTextViewText(R.id.notification_title, content.title)
        setTextViewText(R.id.notification_mood_pill, content.moodLabel)
        // The card is laid out with the light/dark accent resources; when the app theme has its own
        // accent, tint the two accent labels with it. setTextColor is a first-class RemoteViews
        // action, so there is no reflective call that a locked-down SystemUI could reject.
        // These are exactly the views the layouts declare with `@color/notification_card_accent`
        // (the Kotlin contract test asserts that every id set here exists in the layout that
        // renders it), so a theme switch recolours the card in the shade as well as the screens.
        if (accentColor != 0) {
            if (!expanded) {
                listOf(R.id.notification_mood_pill).forEach { setTextColor(it, accentColor) }
            } else {
                listOf(
                    R.id.notification_mood_pill,
                    R.id.notification_next_label,
                ).forEach { setTextColor(it, accentColor) }
            }
        }
        // Minimal is deliberately text only: the badge is declared so every layout accepts the
        // same actions, and hidden here instead of shipping a second set of ids.
        if (badge != null && design != "minimal") setImageViewBitmap(R.id.notification_badge, badge)
        else setViewVisibility(R.id.notification_badge, View.GONE)
        // The banner look tints its leading rule with the day's mood colour, through a first-class
        // RemoteViews action on an ImageView.
        if (design == "banner") {
            setImageViewResource(R.id.notification_accent_rule, accentRuleRes(content.mood))
        }
        setContentDescription(R.id.notification_root,
            if (expanded) content.expandedText else "${content.title}. ${content.summaryLine}")
        if (!expanded) {
            setTextViewText(R.id.notification_compact_summary, content.summaryLine)
        } else {
            setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)

            setViewVisibility(R.id.notification_progress_container,
                if (content.hasProgress) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_progress_label, content.progressLabel)
            // The bar only makes sense when the day has a total; an overdue-only reading is a
            // sentence in the same row, so the track is hidden rather than drawn empty.
            setViewVisibility(R.id.notification_progress, if (content.todayTotal > 0) View.VISIBLE else View.GONE)
            setProgressBar(R.id.notification_progress, content.todayTotal.coerceAtLeast(1), content.todayCompleted, false)

            val showNextSection = content.hasNextTask || !content.hasOccasions
            setViewVisibility(R.id.notification_next_container, if (showNextSection) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_next_label, content.nextTaskLabel)
            setTextViewText(R.id.notification_next_title, content.nextTaskTitle ?: content.fallbackTaskText)
            setTextViewText(R.id.notification_next_time, content.nextTaskTime.orEmpty())
            setViewVisibility(R.id.notification_next_time, if (content.nextTaskTime == null) View.GONE else View.VISIBLE)

            setViewVisibility(R.id.notification_occasions_container, if (content.hasOccasions) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_occasions_text, content.occasionsLine)
        }
    }
}
