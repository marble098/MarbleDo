package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.marble098.marbledo.R

/** Framework-decorated layouts: Android retains its trusted header, expand affordance and actions. */
internal object CalendarNotificationViews {
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
    ): RemoteViews = RemoteViews(context.packageName,
        layout(design, expanded, rtl)).apply {
        setTextViewText(R.id.notification_title, content.title)
        setTextViewText(R.id.notification_mood_pill, content.moodSubtitle)
        if (badge != null && design != "minimal") setImageViewBitmap(R.id.notification_badge, badge)
        else setViewVisibility(R.id.notification_badge, View.GONE)
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
