package com.marble098.marbledo.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.enabledOccasionCultures
import com.marbledo.domain.util.NotificationPlanBuilder
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.Occasion
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.calendar.dayOfMonthIn
import com.marbledo.feature.calendar.enabledOccasionCategories
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The quiet daily planner that keeps the status-bar date and the one next step in the shade.
 *
 * The content model follows the redesign rule: **two sections, in priority order**. The card
 * carries the Jalali date and a state chip, then the *tasks* it wants the user to act on, then —
 * only when the day has any — the *occasions* that are merely interesting. Everything is derived
 * here so Settings can render the same model with Compose and the shade can render it with
 * `RemoteViews`; a string is formatted once and appears identically in both.
 *
 * The card states **one calendar only** — the Jalali date. The Gregorian and lunar cross-check
 * line that used to sit under it was removed: it truncated ("قمری ۹ ج…") on every narrow phone,
 * cost a line the task needed, and duplicated what the calendar screen already shows. A
 * notification is a two-second summary, not a dashboard, so the second calendar lives in the app.
 */
object PersistentCalendarNotification {
    const val NOTIFICATION_ID = 8_412
    private const val CALENDAR_GROUP = "marbledo.calendar"
    // A tagged summary cannot replace an untagged task reminder with the same numeric ID.
    private const val SUMMARY_TAG = "calendar.summary"

    enum class StatusIconState { ACTIVE, OFF, PERMISSION_MISSING, BLOCKED }

    data class NotificationBoxContent(
        /** Day, month and year of the Jalali calendar: the card's only date. */
        val title: String,
        /** The state the chip states: overdue first, then today, then an occasion-only day. */
        val state: NotificationState,
        /** The chip's word: "۳ عقب‌افتاده" or "امروز". Stated once, in the header chip. */
        val stateLabel: String,
        /** The heading of the priority-1 section. */
        val tasksLabel: String,
        /** The task the card is about — the next overdue one, or the next one due today. */
        val taskTitle: String?,
        /** Its due time only ("۱۳:۰۰", or "تمام‌روز" for an all-day task), beside the title. */
        val taskMetaLine: String?,
        /** A second task, expanded only, so two things can be acted on without opening the app. */
        val secondTaskTitle: String?,
        val secondTaskMetaLine: String?,
        /** "و ۲ کار دیگر" when the day has more open tasks than the card shows. */
        val moreTasksLine: String?,
        /** What the card says instead of a title when nothing is due. */
        val emptyTaskText: String,
        val hasTask: Boolean = taskTitle != null,
        /** The day's occasions, in the value column beside the occasion label. */
        val occasionsLine: String,
        /** The heading of the priority-2 section ("مناسبت‌های امروز"). */
        val occasionsLabel: String,
        /** False when the task block should not be closed with a hairline (nothing below it). */
        val occasionsDivider: Boolean = occasionsLine.isNotBlank(),
        /** The task id the card and its actions target; 0 when there is nothing to act on. */
        val focusTaskId: Long?,
        val overdueCount: Int,
        /** The list behind the grouped overdue reminder, already in reading order. */
        val overdueTasks: List<Task>,
        val mood: NotificationMood,
        /** Unchanged day number and mood policy for the status-bar icon. */
        val dayDigits: String,
        /** The large badge belongs to the primary Persian date, not the status-bar calendar. */
        val badgeDayDigits: String,
        val monthLabel: String,
        val hasOccasions: Boolean = occasionsLine.isNotBlank(),
    ) {
        /**
         * Everything a reader needs from the custom card, in the order it is drawn.
         *
         * The state is spoken once, in the header: the card no longer repeats "۳ عقب‌افتاده" over
         * the task list, so neither does TalkBack.
         */
        fun accessibilityText(expanded: Boolean): String {
            val lines = mutableListOf(title, stateLabel)
            if (hasTask) {
                if (expanded) lines += tasksLabel
                lines += taskTitle.orEmpty()
                taskMetaLine?.let { lines += it }
                if (expanded) {
                    secondTaskTitle?.let { lines += it }
                    secondTaskMetaLine?.let { lines += it }
                    moreTasksLine?.let { lines += it }
                }
            } else {
                lines += emptyTaskText
            }
            if (expanded && hasOccasions) {
                lines += occasionsLabel
                lines += occasionsLine
            }
            return lines.filter { it.isNotBlank() }.joinToString(". ")
        }
    }

    @Volatile private var lastPostedSignature: Int = 0

    /**
     * When the current signature was last posted. A notification that SystemUI could not expand
     * stays "active" in [NotificationManager] while nothing is drawn, so freshness is the only
     * signal the app has that a re-post can heal a vanished row.
     */
    @Volatile private var lastPostedAtMillis: Long = 0L

    fun update(context: Context, settings: AppSettings, tasks: List<Task>, catalog: OccasionCatalog,
        nowMillis: Long = System.currentTimeMillis()) {
        if (!settings.persistentDateNotificationEnabled || !canPostNotifications(context)) {
            cancel(context)
            return
        }
        // Notification rendering is a nicety; an OEM rendering failure must never crash the app.
        runCatching { updateInternal(context, settings, tasks, catalog, nowMillis) }
    }

    fun buildContent(context: Context, settings: AppSettings, tasks: List<Task>, catalog: OccasionCatalog,
        nowMillis: Long = System.currentTimeMillis()): NotificationBoxContent {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        val zone = ZoneId.systemDefault()
        val today = PersianDateUtils.today(zone, nowMillis)
        val now = Instant.ofEpochMilli(nowMillis)
        val index = OccasionIndex.build(catalog, listOf(today.year), settings.lunarOffsetDays,
            settings.enabledOccasionCategories(), zone, settings.enabledOccasionCultures())
        val events = index.on(today.year, today.month, today.day)
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        // Do not change the status-bar mark policy: holiday > religious > occasion > today's tasks.
        val mood = NotificationArtwork.mood(events.any(Occasion::isHoliday),
            events.any { it.category == OccasionCategory.RELIGIOUS }, events.isNotEmpty(), plan.todayOpen)
        fun digits(value: String) = TextNormalizer.formatDigits(value, settings.numeralMode)
        val bidi = BidiFormatter.getInstance(settings.languageTag == "fa")
        // The Jalali date, with its year, exactly as the collapsed card shows it: "۲۸ آبان ۱۴۰۵".
        // A date without the year cannot be read at a glance — "۲۸ آبان" of which year? — and the
        // old card hid the year in the secondary line nobody saw.
        val title = digits(
            "${today.day} ${PersianDateUtils.monthName(today.month, settings.languageTag)} ${today.year}",
        )
        val favorites = catalog.canonicalFavoriteIds(settings.favoriteOccasionIds)
        val sortedEvents = events.sortedWith(compareBy<Occasion> { if (it.id in favorites) 0 else 1 }
            .thenBy { if (it.isHoliday) 0 else 1 }.thenBy { it.id })
        val visibleEvents = sortedEvents.take(MAX_VISIBLE_EVENTS)
        val occasionTitles = visibleEvents.map { occasion ->
            bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(occasion.title(settings.languageTag)))
        }
        val hiddenEvents = (events.size - occasionTitles.size).coerceAtLeast(0)
        // One value line. The overflow count rides at the end instead of owning a third row.
        val occasionsLine = (occasionTitles + listOfNotNull(
            hiddenEvents.takeIf { it > 0 }?.let {
                notificationContext.getString(R.string.notification_occasions_overflow, digits(it.toString()))
            },
        )).joinToString(" · ")
        val noTask = notificationContext.getString(R.string.persistent_date_notification_no_task)
        // ---- priority 1: the tasks -----------------------------------------------------------
        // The card is about the next *actionable* task, so the overdue list comes first: it is the
        // one the user can no longer do on time, and hiding it behind a today task was problem #1.
        val focus = plan.overdueTasks.firstOrNull() ?: plan.todayTasks.firstOrNull() ?: plan.nextTask
        val second = (plan.overdueTasks + plan.todayTasks).firstOrNull { it.id != focus?.id }
        val openToday = (plan.todayTasks.size + plan.overdueTasks.size)
        /**
         * The row's metadata is **the clock and nothing else**.
         *
         * It used to be "۲۷ آبان · ۱۳:۰۰ · فوری · بین‌الملل" — the date, the clock, the priority
         * *and* the category — which needs a column wider than the title and still arrived cut to
         * "بین‌الملل…". A clock is what a notification reader is looking for, and a short one always
         * fits the fixed column the layout reserves for it, so nothing on the row is ever ellipsised
         * for want of space. How late a task is lives in the header's chip ("۳ عقب‌افتاده") and the
         * rest of the detail is one tap away on the task itself.
         */
        fun taskTime(task: Task?): String? = task?.dueAtEpochMillis?.let { due ->
            val at = Instant.ofEpochMilli(due).atZone(zone)
            if (task.isAllDay) notificationContext.getString(R.string.notification_all_day)
            else digits(at.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)))
        }
        fun taskMeta(task: Task?): String? = taskTime(task)?.takeIf { it.isNotBlank() }
        val taskTitle = focus?.let { bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(it.title)) }
        val taskMetaLine = taskMeta(focus)
        val secondTaskTitle = second?.let { bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(it.title)) }
        val secondTaskMetaLine = taskMeta(second)
        // A count, not a progress bar: "و ۲ کار دیگر" under the two tasks the card already shows.
        val hiddenTasks = (openToday - listOfNotNull(focus, second).size).coerceAtLeast(0)
        val moreTasksLine = if (hiddenTasks > 0) {
            digits(notificationContext.getString(R.string.notification_more_tasks, hiddenTasks.toString()))
        } else {
            null
        }
        // The state chip: the day's most urgent fact, stated in one word and one colour.
        val state = when {
            plan.overdueCount > 0 -> NotificationState.OVERDUE
            plan.todayOpen > 0 || plan.todayTotal > 0 -> NotificationState.TODAY
            occasionsLine.isNotBlank() -> NotificationState.OCCASION
            else -> NotificationState.QUIET
        }
        val stateLabel = digits(when (state) {
            NotificationState.OVERDUE -> notificationContext.getString(
                R.string.notification_overdue_chip, plan.overdueCount.toString(),
            )
            NotificationState.TODAY -> notificationContext.getString(R.string.notification_today_chip)
            NotificationState.OCCASION -> notificationContext.getString(R.string.notification_occasion_chip)
            NotificationState.QUIET -> notificationContext.getString(R.string.notification_quiet_chip)
        })
        val tasksLabel = notificationContext.getString(R.string.notification_tasks_section)
        val occasionsLabel = notificationContext.getString(R.string.notification_occasions_section)
        return NotificationBoxContent(
            title = title,
            state = state,
            stateLabel = stateLabel,
            tasksLabel = tasksLabel,
            taskTitle = taskTitle,
            taskMetaLine = taskMetaLine?.takeIf { it.isNotBlank() },
            secondTaskTitle = secondTaskTitle,
            secondTaskMetaLine = secondTaskMetaLine?.takeIf { it.isNotBlank() },
            moreTasksLine = moreTasksLine,
            emptyTaskText = noTask,
            occasionsLine = occasionsLine,
            occasionsLabel = occasionsLabel,
            occasionsDivider = occasionsLine.isNotBlank(),
            focusTaskId = focus?.id,
            overdueCount = plan.overdueCount,
            overdueTasks = plan.overdueTasks,
            mood = mood,
            dayDigits = digits(dayOfMonthIn(nowMillis, settings.statusBarCalendar, zone).toString()),
            badgeDayDigits = digits(today.day.toString()),
            monthLabel = PersianDateUtils.monthName(today.month, settings.languageTag),
            // The weekday has no slot on the redesigned header: the Jalali date with its year is
            // the one date the card states, and the chip carries the day's character.
        )
    }

    private fun updateInternal(context: Context, settings: AppSettings, tasks: List<Task>, catalog: OccasionCatalog, nowMillis: Long) {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        NotificationChannels.create(notificationContext)
        val content = buildContent(notificationContext, settings, tasks, catalog, nowMillis)
        // The shade follows the app theme: the section labels, the mood rule, the task plate, the
        // state chip and the date badge are painted from the palette of the theme the user picked,
        // so Ocean/Forest/Sunset/Rose also repaint the notification. Only the semantic states keep
        // their own hues (overdue red, occasion purple), because those carry meaning, not branding.
        // One palette decides the plate, the text and the accents, from the app theme's own
        // lightness. The old card mixed the theme accent with the *system's* night resources, so a
        // pinned theme in the "other" mode got half of the card in the wrong direction.
        val shade = notificationPalette(notificationContext, settings.themeMode)
        val accent = shade.accent.toInt()
        val signature = listOf(content, settings.notificationDesign, settings.themeMode, shade, settings.languageTag, settings.statusBarIconStyle,
            notificationContext.resources.configuration.uiMode,
            notificationContext.resources.displayMetrics.densityDpi, notificationContext.resources.configuration.fontScale).hashCode()
        val active = notificationContext.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
        val isVisible = active.any { it.tag == null && it.id == NOTIFICATION_ID && it.notification.group == CALENDAR_GROUP } &&
            active.any { it.tag == SUMMARY_TAG && it.id == NOTIFICATION_ID }
        // "Active" is not "rendered": if SystemUI failed to expand the custom view it keeps the
        // notification registered while drawing nothing, and a signature-only guard would then
        // never post it again. Re-posting after [REPOST_AFTER_MILLIS] makes the icon heal itself
        // on the next refresh (hourly, at midnight, or when the app comes back to the foreground).
        val freshlyPosted = nowMillis - lastPostedAtMillis in 0..REPOST_AFTER_MILLIS
        if (signature == lastPostedSignature && isVisible && freshlyPosted) return
        fun destination(request: Int, uri: String, route: String) = PendingIntent.getActivity(notificationContext, request,
            Intent(notificationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW; data = Uri.parse(uri)
                putExtra(MainActivity.EXTRA_DESTINATION, route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendarIntent = destination(NOTIFICATION_ID, "marbledo://calendar/today", "calendar")
        val largeIcon = NotificationArtwork.dateBadge(notificationContext, content.badgeDayDigits, content.monthLabel, content.mood, accent)
            ?: NotificationArtwork.tintedFallbackIcon(notificationContext, R.drawable.ic_notification_calendar_large, accent)
        val smallIcon = NotificationArtwork.smallIcon(notificationContext, content.dayDigits, content.mood, settings.statusBarIconStyle)
        val publicVersion = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon).setContentTitle(content.title)
            .setContentText(notificationContext.getString(R.string.notification_private)).setShowWhen(false).build()
        // A RemoteViews that SystemUI cannot inflate takes the whole row down with it, so the
        // custom views are optional: if either one cannot even be built here, the notification is
        // posted from its title/text/actions instead of not being posted at all. The status-bar
        // icon is part of the notification, so "no custom view" still looks far better than
        // "no notification".
        //
        // Use the compact, full-available-width card in the collapsed slot as well as heads-up;
        // otherwise the notification initially looks like a title-only system row. Its layout stays
        // inside Android's 48 dp collapsed-content budget (one task line), while the user-controlled
        // expanded card carries the second task and occasions. SystemUI still owns the outer frame,
        // app header and expansion affordance, so the app cannot force a notification to stay open.
        val compactView = runCatching {
            CalendarNotificationViews.create(
                notificationContext, content, largeIcon, false, settings.notificationDesign,
                settings.languageTag == "fa", accent, shade,
            )
        }.onFailure { Log.w(TAG, "compact ${settings.notificationDesign} view failed; falling back to text", it) }.getOrNull()
        val expandedView = runCatching {
            CalendarNotificationViews.create(
                notificationContext, content, largeIcon, true, settings.notificationDesign,
                settings.languageTag == "fa", accent, shade,
            )
        }.onFailure { Log.w(TAG, "expanded ${settings.notificationDesign} view failed; falling back to text", it) }.getOrNull()
        // The card itself opens the task it is about: a tap on the notification is the fastest
        // route to the thing, so it deep-links to that task and only falls back to the calendar
        // when no task is focused.
        val focusTaskId = content.focusTaskId?.takeIf { it > 0 }
        val cardIntent = if (focusTaskId != null) {
            NotificationActions.openTask(notificationContext, focusTaskId, requestCode = NOTIFICATION_ID)
        } else {
            calendarIntent
        }
        // The platform header states the date and the next task; the state is *not* repeated here.
        // `setSubText(stateLabel)` used to print "۳ عقب‌افتاده" a second time, right above the chip
        // that already says it — the duplicate the redesign was asked to remove.
        val builder = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon)
            .setGroup(CALENDAR_GROUP)
            .setLargeIcon(largeIcon)
            .setColor(accent)
            .setContentTitle(content.title)
            .setContentText(content.taskTitle ?: content.stateLabel)
        if (expandedView != null) {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expandedView)
            compactView?.let {
                builder.setCustomContentView(it)
                    .setCustomHeadsUpContentView(it)
            }
        }
        builder.setContentIntent(cardIntent).setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion).setShowWhen(false).setLocalOnly(true).setSilent(true)
            .setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
        // At most three actions, each with an icon and a TalkBack label. The order is the order of
        // use: finish it, postpone it, open it. The task-level verbs only exist when the card is
        // about a task — on an occasion-only day there is nothing to complete.
        if (focusTaskId != null) {
            builder.addAction(
                R.drawable.ic_notification_done,
                notificationContext.getString(R.string.notification_done),
                NotificationActions.done(notificationContext, focusTaskId, requestCode = NOTIFICATION_ID + 2),
            )
            builder.addAction(
                R.drawable.ic_notification_snooze,
                notificationContext.getString(R.string.notification_snooze),
                NotificationActions.snoozePicker(notificationContext, focusTaskId, requestCode = NOTIFICATION_ID + 3),
            )
            builder.addAction(
                R.drawable.ic_notification_open,
                notificationContext.getString(R.string.notification_open_task),
                cardIntent,
            )
        } else {
            builder.addAction(
                R.drawable.ic_notification_open,
                notificationContext.getString(R.string.calendar_notification_open),
                calendarIntent,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(notificationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val summary = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setGroup(CALENDAR_GROUP).setGroupSummary(true)
            .setSmallIcon(smallIcon).setColor(accent)
            .setContentTitle(notificationContext.getString(R.string.app_name)).setContentText(content.title)
            .setContentIntent(calendarIntent).setPublicVersion(publicVersion)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setShowWhen(false)
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(true).setLocalOnly(true)
        val manager = NotificationManagerCompat.from(notificationContext)
        manager.notify(NOTIFICATION_ID, builder.build())
        manager.notify(SUMMARY_TAG, NOTIFICATION_ID, summary.build())
        lastPostedSignature = signature
        lastPostedAtMillis = nowMillis
    }

    fun cancel(context: Context) {
        lastPostedSignature = 0
        lastPostedAtMillis = 0L
        runCatching {
            val manager = NotificationManagerCompat.from(context.applicationContext)
            manager.cancel(NOTIFICATION_ID)
            manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
        }
    }

    /** What the status-bar icon currently needs, shown as a diagnostics line in Settings. */
    fun statusIconState(context: Context, enabled: Boolean): StatusIconState = when {
        !enabled -> StatusIconState.OFF
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
            StatusIconState.PERMISSION_MISSING
        !canPostNotifications(context) || !channelAllowed(context) -> StatusIconState.BLOCKED
        else -> StatusIconState.ACTIVE
    }

    private fun channelAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationChannels.PERSISTENT_DATE)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun Context.forLanguage(languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(resources.configuration).apply {
            setLocale(locale)
            @Suppress("DEPRECATION")
            setLayoutDirection(locale)
        }
        return createConfigurationContext(configuration)
    }

    private const val MAX_VISIBLE_EVENTS = 2

    /** How long a posted notification is trusted as "already on screen" before it is re-sent. */
    internal const val REPOST_AFTER_MILLIS = 30 * 60 * 1000L
    private const val TAG = "MarbleDateIcon"
}
