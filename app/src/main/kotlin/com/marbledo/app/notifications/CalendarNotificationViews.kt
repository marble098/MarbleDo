package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.marble098.marbledo.R

/** Framework-decorated layouts: Android retains its trusted header, expand affordance and actions. */
internal object CalendarNotificationViews {
    fun create(
        context: Context,
        content: PersistentCalendarNotification.NotificationBoxContent,
        badge: Bitmap?,
        expanded: Boolean,
    ): RemoteViews = RemoteViews(context.packageName,
        if (expanded) R.layout.notification_calendar_expanded else R.layout.notification_calendar_compact).apply {
        setTextViewText(R.id.notification_title, content.title)
        if (badge != null) setImageViewBitmap(R.id.notification_badge, badge)
        else setViewVisibility(R.id.notification_badge, View.GONE)
        setContentDescription(R.id.notification_root, if (expanded) content.expandedText else "${content.title}. ${content.collapsedLine}")
        if (!expanded) {
            setTextViewText(R.id.notification_compact_summary, content.collapsedLine)
        } else {
            setTextViewText(R.id.notification_secondary_dates, content.secondaryDatesLine)
            setTextViewText(R.id.notification_progress_label, content.progressSummary)
            setProgressBar(R.id.notification_progress, content.todayTotal.coerceAtLeast(1), content.todayCompleted, false)
            setTextViewText(R.id.notification_next_label, content.nextTaskLabel)
            setTextViewText(R.id.notification_next_title, content.nextTaskTitle ?: content.fallbackTaskText)
            setTextViewText(R.id.notification_next_time, content.nextTaskTime.orEmpty())
            setViewVisibility(R.id.notification_next_time, if (content.nextTaskTime == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.notification_occasions_label, context.getString(R.string.notification_occasions))
            setTextViewText(R.id.notification_occasions_text, content.occasionLines.joinToString("\n").ifEmpty { content.fallbackOccasionText })
            setTextViewText(R.id.notification_more_occasions, content.moreOccasionsText)
            setViewVisibility(R.id.notification_more_occasions, if (content.moreOccasionsCount > 0) View.VISIBLE else View.GONE)
        }
    }
}
