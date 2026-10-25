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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.BidiFormatter
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.NumeralMode
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

/** Quiet daily planner with one content model shared by the real notification and Settings preview. */
object PersistentCalendarNotification {
    const val NOTIFICATION_ID = 8_412
    enum class StatusIconState { ACTIVE, OFF, PERMISSION_MISSING, BLOCKED }

    data class NotificationBoxContent(
        val title: String,
        val moodSubtitle: String,
        val secondaryDatesLine: String,
        val collapsedLine: String,
        val occasionLines: List<String>,
        val moreOccasionsCount: Int,
        val moreOccasionsText: String,
        val nextTaskSummary: String?,
        val nextTaskId: Long?,
        val nextTaskTitle: String?,
        val nextTaskTime: String?,
        val nextTaskLabel: String,
        val todayCompleted: Int,
        val todayTotal: Int,
        val progressSummary: String,
        val fallbackOccasionText: String,
        val fallbackTaskText: String,
        val expandedText: String,
        val mood: NotificationMood,
        /** Unchanged day number and mood policy for the status-bar icon. */
        val dayDigits: String,
        /** The large badge belongs to the primary Persian date, not the status-bar calendar. */
        val badgeDayDigits: String,
        val monthLabel: String,
        val weekdayLabel: String,
        val badgeCount: Int,
        val hasProgress: Boolean = todayTotal > 0,
        val hasNextTask: Boolean = nextTaskTitle != null,
        val hasOccasions: Boolean = occasionLines.isNotEmpty(),
    )

    @Volatile private var lastPostedSignature: Int = 0

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
        val todayDate = now.atZone(zone).toLocalDate()
        val index = OccasionIndex.build(catalog, listOf(today.year), settings.lunarOffsetDays,
            settings.enabledOccasionCategories(), zone, settings.enabledOccasionCultures())
        val events = index.on(today.year, today.month, today.day)
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        // Do not change the status-bar mark policy: holiday > religious > occasion > today's tasks.
        val mood = NotificationArtwork.mood(events.any(Occasion::isHoliday),
            events.any { it.category == OccasionCategory.RELIGIOUS }, events.isNotEmpty(), plan.todayOpen)
        val locale = firstLocale(notificationContext)
        val title = PersianDateUtils.fullDateWithWeekday(nowMillis, settings.languageTag, settings.numeralMode, zone)
        val gregorian = PersianDateUtils.gregorianDate(nowMillis, locale, zone)
        val lunar = PersianDateUtils.lunarDate(nowMillis, settings.languageTag, NumeralMode.LATIN, settings.lunarOffsetDays, zone)
        fun digits(value: String) = TextNormalizer.formatDigits(value, settings.numeralMode)
        val secondary = digits("$gregorian  ·  $lunar")
        val bidi = BidiFormatter.getInstance(settings.languageTag == "fa")
        val favorites = catalog.canonicalFavoriteIds(settings.favoriteOccasionIds)
        val sortedEvents = events.sortedWith(compareBy<Occasion> { if (it.id in favorites) 0 else 1 }
            .thenBy { if (it.isHoliday) 0 else 1 }.thenBy { it.id })
        val visibleEvents = sortedEvents.take(MAX_VISIBLE_EVENTS)
        val occasionTitles = visibleEvents.map { occasion ->
            val wrapped = bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(occasion.title(settings.languageTag)))
            if (visibleEvents.size > 1) "• $wrapped" else wrapped
        }
        val firstOccasionPlain = visibleEvents.firstOrNull()?.let {
            bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(it.title(settings.languageTag)))
        }
        val hiddenEvents = (events.size - occasionTitles.size).coerceAtLeast(0)
        val moreText = if (hiddenEvents > 0) digits(notificationContext.getString(R.string.notification_more, hiddenEvents)) else ""
        val noOccasion = notificationContext.getString(R.string.persistent_date_notification_no_occasion)
        val noTask = notificationContext.getString(R.string.persistent_date_notification_no_task)
        val next = plan.nextTask
        val nextTitle = next?.let { bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(it.title)) }
        val nextTime = next?.dueAtEpochMillis?.let { due ->
            val instant = Instant.ofEpochMilli(due)
            val date = instant.atZone(zone).toLocalDate()
            val clock = if (next?.isAllDay == true) notificationContext.getString(R.string.notification_all_day)
                else digits(instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)))
            if (date == todayDate) notificationContext.getString(R.string.notification_due_today, clock)
            else "${PersianDateUtils.fullDate(due, settings.languageTag, settings.numeralMode, zone)} · $clock"
        }
        val nextSummary = nextTitle?.let { "$it · $nextTime" }
        val nextLabel = notificationContext.getString(if (plan.nextIsOverdue) R.string.notification_overdue else R.string.notification_next)
        val progress = digits(notificationContext.getString(R.string.notification_today_progress, plan.todayCompleted, plan.todayTotal))
        val workload = digits(notificationContext.getString(
            if (plan.overdueCount > 0) R.string.notification_overdue_count else R.string.notification_today_count,
            if (plan.overdueCount > 0) plan.overdueCount else plan.todayOpen))
        val progressSummary = if (plan.todayTotal > 0) "$progress · $workload" else workload
        val moodSubtitle = notificationContext.getString(when (mood) {
            NotificationMood.HOLIDAY -> R.string.persistent_date_notification_title_holiday
            NotificationMood.RELIGIOUS -> R.string.persistent_date_notification_title_religious
            NotificationMood.OCCASION -> R.string.persistent_date_notification_title_occasion
            NotificationMood.TASKS_DUE -> R.string.persistent_date_notification_title_tasks
            NotificationMood.QUIET -> R.string.persistent_date_notification_title
        })
        val collapsed = when {
            nextSummary != null && firstOccasionPlain != null && !plan.nextIsOverdue -> "$nextSummary  ·  $firstOccasionPlain"
            nextSummary != null -> nextSummary
            firstOccasionPlain != null -> if (events.size > 1) "$firstOccasionPlain · +${digits((events.size - 1).toString())}" else firstOccasionPlain
            else -> secondary
        }
        val expandedText = listOf(secondary, progressSummary.takeIf { plan.todayTotal > 0 || plan.overdueCount > 0 }.orEmpty(),
            nextLabel.takeIf { nextSummary != null || occasionTitles.isEmpty() }.orEmpty(),
            nextSummary ?: if (occasionTitles.isEmpty()) noTask else "",
            notificationContext.getString(R.string.notification_occasions).takeIf { occasionTitles.isNotEmpty() }.orEmpty(),
            occasionTitles.joinToString("\n"), moreText)
            .filter { it.isNotBlank() }.joinToString("\n")
        return NotificationBoxContent(title, moodSubtitle, secondary, collapsed, occasionTitles, hiddenEvents, moreText,
            nextSummary, next?.id, nextTitle, nextTime, nextLabel, plan.todayCompleted, plan.todayTotal, progressSummary,
            noOccasion, noTask, expandedText, mood,
            digits(dayOfMonthIn(nowMillis, settings.statusBarCalendar, zone).toString()), digits(today.day.toString()),
            PersianDateUtils.monthName(today.month, settings.languageTag), PersianDateUtils.weekdayName(today.weekdayIndex, settings.languageTag),
            if (plan.todayOpen > 0) plan.todayOpen else events.size,
            hasProgress = plan.todayTotal > 0 || plan.overdueCount > 0,
            hasNextTask = nextTitle != null,
            hasOccasions = occasionTitles.isNotEmpty())
    }

    private fun updateInternal(context: Context, settings: AppSettings, tasks: List<Task>, catalog: OccasionCatalog, nowMillis: Long) {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        NotificationChannels.create(notificationContext)
        val content = buildContent(notificationContext, settings, tasks, catalog, nowMillis)
        val signature = listOf(content, settings.notificationDesign, settings.languageTag, settings.statusBarIconStyle, notificationContext.resources.configuration.uiMode,
            notificationContext.resources.displayMetrics.densityDpi, notificationContext.resources.configuration.fontScale).hashCode()
        val isVisible = notificationContext.getSystemService(NotificationManager::class.java)?.activeNotifications
            ?.any { it.id == NOTIFICATION_ID } == true
        if (signature == lastPostedSignature && isVisible) return
        fun destination(request: Int, uri: String, route: String) = PendingIntent.getActivity(notificationContext, request,
            Intent(notificationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW; data = Uri.parse(uri)
                putExtra(MainActivity.EXTRA_DESTINATION, route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendarIntent = destination(NOTIFICATION_ID, "marbledo://calendar/today", "calendar")
        val tasksIntent = destination(NOTIFICATION_ID + 1, "marbledo://home/tasks", "home")
        val largeIcon = NotificationArtwork.dateBadge(notificationContext, content.badgeDayDigits, content.monthLabel, content.mood)
            ?: ContextCompat.getDrawable(notificationContext, R.drawable.ic_notification_calendar_large)?.toBitmap()
        val smallIcon = NotificationArtwork.smallIcon(notificationContext, content.dayDigits, content.mood, settings.statusBarIconStyle)
        val publicVersion = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon).setContentTitle(content.title)
            .setContentText(notificationContext.getString(R.string.notification_private)).setShowWhen(false).build()
        val builder = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon)
            .setColor(NotificationArtwork.accentColor(notificationContext, content.mood))
            .setContentTitle(content.title).setContentText(content.collapsedLine).setSubText(content.moodSubtitle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(CalendarNotificationViews.create(notificationContext, content, largeIcon, false, settings.notificationDesign, settings.languageTag == "fa"))
            .setCustomBigContentView(CalendarNotificationViews.create(notificationContext, content, largeIcon, true, settings.notificationDesign, settings.languageTag == "fa"))
            .setContentIntent(calendarIntent).setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion).setShowWhen(false).setLocalOnly(true).setSilent(true)
            .setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
        content.nextTaskId?.takeIf { it > 0 }?.let { taskId ->
            val done = PendingIntent.getBroadcast(notificationContext, NOTIFICATION_ID + 2,
                Intent(notificationContext, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE)
                    .setData(Uri.parse("marbledo://action/done/$taskId")).putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_stat_marble, notificationContext.getString(R.string.notification_done_next), done)
        }
        builder.addAction(R.drawable.ic_stat_calendar, notificationContext.getString(R.string.calendar_notification_open), calendarIntent)
            .addAction(R.drawable.ic_stat_marble, notificationContext.getString(R.string.persistent_date_notification_open_tasks), tasksIntent)
        if (content.badgeCount > 0) builder.setNumber(content.badgeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(notificationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(notificationContext).notify(NOTIFICATION_ID, builder.build())
        lastPostedSignature = signature
    }

    fun cancel(context: Context) {
        lastPostedSignature = 0
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID) }
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

    /** The primary locale, falling back to a sensible default instead of crashing on an empty list. */
    private fun firstLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.forLanguageTag("fa")
    }

    private const val MAX_VISIBLE_EVENTS = 2
}
