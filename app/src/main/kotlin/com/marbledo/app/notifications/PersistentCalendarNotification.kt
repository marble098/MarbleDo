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
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.BidiFormatter
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.core.designsystem.MarblePalettes
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

/**
 * The quiet daily planner that keeps the status-bar date and the one next step in the shade.
 *
 * The content model follows the redesign rule: **one row, one idea**. The card no longer carries
 * a mood pill *and* a task count *and* a labelled section for occasions *and* a labelled block for
 * the next task; it carries a date, the day's character, one progress reading, one next step and
 * — when there is one — one line of occasions. Everything is derived here so Settings can render
 * the same model with Compose and the shade can render it with `RemoteViews`.
 */
object PersistentCalendarNotification {
    const val NOTIFICATION_ID = 8_412
    enum class StatusIconState { ACTIVE, OFF, PERMISSION_MISSING, BLOCKED }

    data class NotificationBoxContent(
        /** Weekday, day and month of the primary calendar. The year belongs to the secondary line. */
        val title: String,
        /** One or two words for the character of the day: holiday, religious, occasion, tasks, today. */
        val moodLabel: String,
        /** Gregorian and lunar dates, one line, for the reader who needs a cross-check. */
        val secondaryDatesLine: String,
        /** The single fact the collapsed row shows under the date. */
        val summaryLine: String,
        /** The day's occasions as one value line, never a labelled section. */
        val occasionsLine: String,
        val nextTaskId: Long?,
        val nextTaskTitle: String?,
        val nextTaskTime: String?,
        val nextTaskLabel: String,
        val todayCompleted: Int,
        val todayTotal: Int,
        val progressLabel: String,
        val fallbackTaskText: String,
        val expandedText: String,
        val mood: NotificationMood,
        /** Unchanged day number and mood policy for the status-bar icon. */
        val dayDigits: String,
        /** The large badge belongs to the primary Persian date, not the status-bar calendar. */
        val badgeDayDigits: String,
        val monthLabel: String,
        val weekdayLabel: String,
        val hasOccasions: Boolean = occasionsLine.isNotBlank(),
        val hasProgress: Boolean = todayTotal > 0 || progressLabel.isNotBlank(),
        val hasNextTask: Boolean = nextTaskTitle != null,
    )

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
        val todayDate = now.atZone(zone).toLocalDate()
        val index = OccasionIndex.build(catalog, listOf(today.year), settings.lunarOffsetDays,
            settings.enabledOccasionCategories(), zone, settings.enabledOccasionCultures())
        val events = index.on(today.year, today.month, today.day)
        val plan = NotificationPlanBuilder.build(tasks, now, zone)
        // Do not change the status-bar mark policy: holiday > religious > occasion > today's tasks.
        val mood = NotificationArtwork.mood(events.any(Occasion::isHoliday),
            events.any { it.category == OccasionCategory.RELIGIOUS }, events.isNotEmpty(), plan.todayOpen)
        val locale = firstLocale(notificationContext)
        fun digits(value: String) = TextNormalizer.formatDigits(value, settings.numeralMode)
        val bidi = BidiFormatter.getInstance(settings.languageTag == "fa")
        // Weekday + day + month. The old title repeated the year, which is the one part of a date
        // nobody reads in a status bar; it now lives in the secondary line.
        val title = digits("${PersianDateUtils.weekdayName(today.weekdayIndex, settings.languageTag)} " +
            "${today.day} ${PersianDateUtils.monthName(today.month, settings.languageTag)}")
        val gregorian = PersianDateUtils.gregorianDate(nowMillis, locale, zone)
        val lunar = PersianDateUtils.lunarDate(nowMillis, settings.languageTag, NumeralMode.LATIN,
            settings.lunarOffsetDays, zone)
        val secondary = digits("$gregorian · $lunar")
        val moodLabel = notificationContext.getString(when (mood) {
            NotificationMood.HOLIDAY -> R.string.notification_mood_holiday
            NotificationMood.RELIGIOUS -> R.string.notification_mood_religious
            NotificationMood.OCCASION -> R.string.notification_mood_occasion
            NotificationMood.TASKS_DUE -> R.string.notification_mood_tasks
            NotificationMood.QUIET -> R.string.notification_mood_today
        })
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
        val next = plan.nextTask
        val nextTitle = next?.let { bidi.unicodeWrap(NotificationPlanBuilder.previewTitle(it.title)) }
        val nextTime = next?.dueAtEpochMillis?.let { due ->
            val instant = Instant.ofEpochMilli(due)
            val date = instant.atZone(zone).toLocalDate()
            val clock = if (next?.isAllDay == true) notificationContext.getString(R.string.notification_all_day)
                else digits(instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)))
            val dayLabel = when {
                date == todayDate -> ""
                date == todayDate.plusDays(1) -> notificationContext.getString(R.string.dash_days_tomorrow)
                else -> {
                    val jalali = PersianDateUtils.of(due, zone)
                    digits("${jalali.day} ${PersianDateUtils.monthName(jalali.month, settings.languageTag)}")
                }
            }
            listOf(dayLabel, clock).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val nextSummary = nextTitle?.let { listOf(it, nextTime.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ") }
        val nextLabel = notificationContext.getString(
            if (plan.nextIsOverdue) R.string.notification_focus_overdue else R.string.notification_focus_next,
        )
        // Progress is one reading: "3 of 5", with the overdue count appended only when it exists.
        val progress = if (plan.todayTotal > 0) digits(notificationContext.getString(
            R.string.notification_progress_pair, plan.todayCompleted.toString(), plan.todayTotal.toString(),
        )) else ""
        val overdueLine = if (plan.overdueCount > 0) digits(
            notificationContext.getString(R.string.notification_overdue_count, plan.overdueCount),
        ) else ""
        val progressLabel = listOf(progress, overdueLine).filter { it.isNotBlank() }.joinToString(" · ")
        // The collapsed row states one fact, in order of what the user can act on next.
        val summaryLine = when {
            nextSummary != null -> nextSummary
            occasionsLine.isNotBlank() -> occasionsLine
            progressLabel.isNotBlank() -> progressLabel
            else -> secondary
        }
        val expandedText = listOf(
            title,
            moodLabel,
            secondary,
            progressLabel,
            if (nextTitle != null) "$nextLabel: $nextTitle ${nextTime.orEmpty()}".trim() else noTask,
            occasionsLine,
        ).filter { it.isNotBlank() }.joinToString(". ")
        return NotificationBoxContent(
            title = title,
            moodLabel = moodLabel,
            secondaryDatesLine = secondary,
            summaryLine = summaryLine,
            occasionsLine = occasionsLine,
            nextTaskId = next?.id,
            nextTaskTitle = nextTitle,
            nextTaskTime = nextTime,
            nextTaskLabel = nextLabel,
            todayCompleted = plan.todayCompleted,
            todayTotal = plan.todayTotal,
            progressLabel = progressLabel,
            fallbackTaskText = noTask,
            expandedText = expandedText,
            mood = mood,
            dayDigits = digits(dayOfMonthIn(nowMillis, settings.statusBarCalendar, zone).toString()),
            badgeDayDigits = digits(today.day.toString()),
            monthLabel = PersianDateUtils.monthName(today.month, settings.languageTag),
            weekdayLabel = PersianDateUtils.weekdayName(today.weekdayIndex, settings.languageTag),
        )
    }

    private fun updateInternal(context: Context, settings: AppSettings, tasks: List<Task>, catalog: OccasionCatalog, nowMillis: Long) {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        NotificationChannels.create(notificationContext)
        val content = buildContent(notificationContext, settings, tasks, catalog, nowMillis)
        // The shade follows the app theme: the accent labels, the mood rule, the progress line,
        // the next-step plate and the date badge are painted from the palette of the theme the
        // user picked, so Ocean/Forest/Sunset/Rose also repaint the notification. Only the small
        // mood chip keeps its semantic colour (holiday, religious, occasion, tasks).
        val accent = MarblePalettes.resolve(
            settings.themeMode,
            MarblePalettes.isDark(settings.themeMode.name, isNightMode(notificationContext)),
            notificationContext,
        ).primary.toInt()
        val signature = listOf(content, settings.notificationDesign, settings.themeMode, settings.languageTag, settings.statusBarIconStyle, notificationContext.resources.configuration.uiMode,
            notificationContext.resources.displayMetrics.densityDpi, notificationContext.resources.configuration.fontScale).hashCode()
        val isVisible = notificationContext.getSystemService(NotificationManager::class.java)?.activeNotifications
            ?.any { it.id == NOTIFICATION_ID } == true
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
            ?: ContextCompat.getDrawable(notificationContext, R.drawable.ic_notification_calendar_large)?.toBitmap()
        val smallIcon = NotificationArtwork.smallIcon(notificationContext, content.dayDigits, content.mood, settings.statusBarIconStyle)
        val publicVersion = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon).setContentTitle(content.title)
            .setContentText(notificationContext.getString(R.string.notification_private)).setShowWhen(false).build()
        // A RemoteViews that SystemUI cannot inflate takes the whole row down with it, so the
        // custom views are optional: if either one cannot even be built here, the notification is
        // posted from its title/text/actions instead of not being posted at all. The status-bar
        // icon is part of the notification, so "no custom view" still looks far better than
        // "no notification".
        val collapsedView = runCatching {
            CalendarNotificationViews.create(notificationContext, content, largeIcon, false, settings.notificationDesign, settings.languageTag == "fa", accent)
        }.onFailure { Log.w(TAG, "collapsed ${settings.notificationDesign} view failed; falling back to text", it) }.getOrNull()
        val expandedView = runCatching {
            CalendarNotificationViews.create(notificationContext, content, largeIcon, true, settings.notificationDesign, settings.languageTag == "fa", accent)
        }.onFailure { Log.w(TAG, "expanded ${settings.notificationDesign} view failed; falling back to text", it) }.getOrNull()
        val builder = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(smallIcon)
            .setColor(NotificationArtwork.accentColor(notificationContext, content.mood))
            .setContentTitle(content.title).setContentText(content.summaryLine)
        if (collapsedView != null && expandedView != null) {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
        }
        builder.setContentIntent(calendarIntent).setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion).setShowWhen(false).setLocalOnly(true).setSilent(true)
            .setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
        // Two actions, not three: the shade is not a toolbar. The whole card already opens the
        // calendar, so the only extra verbs are "mark this one done" and "open the day".
        content.nextTaskId?.takeIf { it > 0 }?.let { taskId ->
            val done = PendingIntent.getBroadcast(notificationContext, NOTIFICATION_ID + 2,
                Intent(notificationContext, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE)
                    .setData(Uri.parse("marbledo://action/done/$taskId")).putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_stat_marble, notificationContext.getString(R.string.notification_done_next), done)
        }
        builder.addAction(R.drawable.ic_stat_calendar, notificationContext.getString(R.string.calendar_notification_open), calendarIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(notificationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(notificationContext).notify(NOTIFICATION_ID, builder.build())
        lastPostedSignature = signature
        lastPostedAtMillis = nowMillis
    }

    fun cancel(context: Context) {
        lastPostedSignature = 0
        lastPostedAtMillis = 0L
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

    /** True when the device is in dark mode, which the un-pinned themes follow. */
    private fun isNightMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

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

    /** How long a posted notification is trusted as "already on screen" before it is re-sent. */
    internal const val REPOST_AFTER_MILLIS = 30 * 60 * 1000L
    private const val TAG = "MarbleDateIcon"
}
