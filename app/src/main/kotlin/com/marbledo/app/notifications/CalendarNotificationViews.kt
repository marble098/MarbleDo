package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.marble098.marbledo.R

/** Framework-decorated layouts: Android retains its trusted header, expand affordance and actions. */
internal object CalendarNotificationViews {

    /**
     * The banner theme's accent rule, already tinted for the day's mood.
     *
     * The rule used to be tinted reflectively, by name, with a "setBackgroundColor" method call —
     * the only reflective action in the whole builder. Reflection is the fragile half of
     * RemoteViews: the method must exist on the target class *and* carry `@RemotableViewMethod`,
     * and a single action that cannot run aborts the entire apply, so the row — and with it the
     * status-bar icon — never renders. A drawable per mood turns that into a first-class
     * `setImageViewResource()`, which has no lookup to fail. It also keeps the rule's rounded
     * corner, which a flat background colour used to erase.
     */
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
        setTextViewText(R.id.notification_mood_pill, content.moodSubtitle)
        // The card is laid out with the light/dark accent resources; when the app theme has its own
        // accent, tint the accent text with it. setTextColor is a first-class RemoteViews action, so
        // there is no reflective call that a locked-down SystemUI could reject.
        // These are exactly the views the layouts declare with `@color/notification_card_accent` (the
        // Kotlin contract test asserts that every id set here exists in the layout that renders it),
        // so a theme switch recolours the card in the shade as well as the screens.
        if (accentColor != 0) {
            if (!expanded) {
                listOf(R.id.notification_mood_pill).forEach { setTextColor(it, accentColor) }
            } else {
                listOf(
                    R.id.notification_mood_pill,
                    R.id.notification_next_label,
                    R.id.notification_occasions_label,
                ).forEach { setTextColor(it, accentColor) }
            }
        }
        if (badge != null && design != "minimal") setImageViewBitmap(R.id.notification_badge, badge)
        else setViewVisibility(R.id.notification_badge, View.GONE)
        // The banner theme tints its leading accent rule with the day's mood colour, through a
        // first-class RemoteViews action on an ImageView.
        if (design == "banner") {
            setImageViewResource(R.id.notification_accent_rule, accentRuleRes(content.mood))
        }
        setContentDescription(R.id.notification_root, if (expanded) content.expandedText else "${content.title}. ${content.collapsedLine}")
        if (!expanded) {
            setTextViewText(R.id.notification_compact_summary, content.collapsedLine)
        } else {
            setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)
            setViewVisibility(R.id.notification_progress_container, if (content.hasProgress) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_progress_label, content.progressSummary)
            setProgressBar(R.id.notification_progress, content.todayTotal.coerceAtLeast(1), content.todayCompleted, false)

            val showNextSection = content.hasNextTask || !content.hasOccasions
            setViewVisibility(R.id.notification_next_container, if (showNextSection) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_next_label, content.nextTaskLabel)
            setTextViewText(R.id.notification_next_title, content.nextTaskTitle ?: content.fallbackTaskText)
            setTextViewText(R.id.notification_next_time, content.nextTaskTime.orEmpty())
            setViewVisibility(R.id.notification_next_time, if (content.nextTaskTime == null) View.GONE else View.VISIBLE)

            setViewVisibility(R.id.notification_occasions_container, if (content.hasOccasions) View.VISIBLE else View.GONE)
            setTextViewText(R.id.notification_occasions_label, context.getString(R.string.notification_occasions))
            setTextViewText(R.id.notification_occasions_text, content.occasionLines.joinToString("\n").ifEmpty { content.fallbackOccasionText })
            setTextViewText(R.id.notification_more_occasions, content.moreOccasionsText)
            setViewVisibility(R.id.notification_more_occasions, if (content.moreOccasionsCount > 0) View.VISIBLE else View.GONE)
        }
    }
}
