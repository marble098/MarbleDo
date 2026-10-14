package com.marble098.marbledo.notifications

import com.marbledo.feature.calendar.dayOfMonthIn
import android.app.NotificationManager
import android.Manifest
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
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.Occasion
import com.marbledo.feature.calendar.enabledOccasionCategories
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Builds the quiet, user-enabled daily date and next-task notification.
 *
 * The artwork is state-aware: the status-bar icon shows today's day number together with a glyph
 * for the day's mood (holiday, religious occasion, occasion or tasks due), the large icon is a
 * generated date badge, and the accent color and title follow the same mood.
 */
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
        val nextTaskSummary: String?,
        val fallbackOccasionText: String,
        val fallbackTaskText: String,
        val expandedText: String,
        val mood: NotificationMood,
        val dayDigits: String,
        val monthLabel: String,
        val weekdayLabel: String,
        val badgeCount: Int,
    )

    @Volatile private var lastPostedSignature: Int = 0

    fun update(
        context: Context,
        settings: AppSettings,
        tasks: List<Task>,
        catalog: OccasionCatalog,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!settings.persistentDateNotificationEnabled) {
            cancel(context)
            return
        }
        if (!canPostNotifications(context)) {
            cancel(context)
            return
        }
        // The status-bar notification is a nicety; building it must never crash the app.
        runCatching { updateInternal(context, settings, tasks, catalog, nowMillis) }
    }

    fun buildContent(
        context: Context,
        settings: AppSettings,
        tasks: List<Task>,
        catalog: OccasionCatalog,
        nowMillis: Long = System.currentTimeMillis(),
    ): NotificationBoxContent {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        val zone = ZoneId.systemDefault()
        val today = PersianDateUtils.today(zone, nowMillis)
        val todayLocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val enabledCategories = settings.enabledOccasionCategories()
        val index = OccasionIndex.build(
            catalog = catalog,
            jalaliYears = listOf(today.year),
            lunarOffsetDays = settings.lunarOffsetDays,
            enabledCategories = enabledCategories,
            zone = zone,
        )
        val events = index.on(today.year, today.month, today.day)
        val tasksDueToday = tasks.count { task ->
            !task.isCompleted && !task.isArchived &&
                task.dueAtEpochMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == todayLocalDate
                } == true
        }
        val mood = NotificationArtwork.mood(
            hasHoliday = events.any(Occasion::isHoliday),
            hasReligiousOccasion = events.any { it.category == OccasionCategory.RELIGIOUS },
            hasOccasion = events.isNotEmpty(),
            tasksDueToday = tasksDueToday,
        )
        val locale = firstLocale(notificationContext)
        val primaryDateTitle = PersianDateUtils.fullDateWithWeekday(
            epochMillis = nowMillis,
            languageTag = settings.languageTag,
            numeralMode = settings.numeralMode,
            zone = zone,
        )
        val gregorian = PersianDateUtils.gregorianDate(nowMillis, locale, zone)
        val lunar = PersianDateUtils.lunarDate(nowMillis, settings.languageTag, NumeralMode.LATIN, settings.lunarOffsetDays, zone)
        val secondaryDatesLine = TextNormalizer.formatDigits("$gregorian  ·  $lunar", settings.numeralMode)

        val bidi = BidiFormatter.getInstance(settings.languageTag == "fa")
        val occasionTitles = events.take(MAX_VISIBLE_EVENTS).map { bidi.unicodeWrap(it.title(settings.languageTag)) }
        val hiddenEvents = (events.size - MAX_VISIBLE_EVENTS).coerceAtLeast(0)
        val noOccasionText = notificationContext.getString(R.string.persistent_date_notification_no_occasion)
        val noTaskText = notificationContext.getString(R.string.persistent_date_notification_no_task)

        val nextTask = nextDatedTask(tasks, nowMillis)
        val nextTaskSummary = nextTask?.let { task ->
            val dueText = task.dueAtEpochMillis?.let {
                PersianDateUtils.fullDate(it, settings.languageTag, settings.numeralMode, zone)
            }
            listOfNotNull(bidi.unicodeWrap(task.title), dueText).joinToString(" · ")
        }

        val moodSubtitle = when (mood) {
            NotificationMood.HOLIDAY -> notificationContext.getString(R.string.persistent_date_notification_title_holiday)
            NotificationMood.RELIGIOUS -> notificationContext.getString(R.string.persistent_date_notification_title_religious)
            NotificationMood.OCCASION -> notificationContext.getString(R.string.persistent_date_notification_title_occasion)
            NotificationMood.TASKS_DUE -> notificationContext.getString(R.string.persistent_date_notification_title_tasks)
            NotificationMood.QUIET -> notificationContext.getString(R.string.persistent_date_notification_title)
        }

        val collapsedLine = when {
            occasionTitles.isNotEmpty() && nextTaskSummary != null ->
                "✦ ${occasionTitles.first()}  ·  ⏳ ${bidi.unicodeWrap(nextTask.title)}"
            occasionTitles.isNotEmpty() -> {
                val base = "✦ ${occasionTitles.first()}"
                if (events.size > 1) {
                    "$base (+${TextNormalizer.formatDigits((events.size - 1).toString(), settings.numeralMode)})"
                } else {
                    base
                }
            }
            nextTaskSummary != null -> "⏳ $nextTaskSummary"
            else -> secondaryDatesLine
        }

        val expandedLines = buildList {
            add(secondaryDatesLine)
            if (occasionTitles.isEmpty()) {
                add("• ${notificationContext.getString(R.string.persistent_date_notification_occasion_label, noOccasionText)}")
            } else {
                occasionTitles.forEachIndexed { idx, item ->
                    if (idx == occasionTitles.lastIndex && hiddenEvents > 0) {
                        val moreDigits = TextNormalizer.formatDigits(hiddenEvents.toString(), settings.numeralMode)
                        add("✦ ${notificationContext.getString(R.string.persistent_date_notification_more_events, item, moreDigits)}")
                    } else {
                        add("✦ $item")
                    }
                }
            }
            if (nextTaskSummary != null) {
                add("⏳ ${notificationContext.getString(R.string.persistent_date_notification_next_task_label, nextTaskSummary)}")
            } else {
                add("• ${notificationContext.getString(R.string.persistent_date_notification_next_task_label, noTaskText)}")
            }
        }
        val statusDay = dayOfMonthIn(nowMillis, settings.statusBarCalendar, zone)
        val dayDigits = TextNormalizer.formatDigits(statusDay.toString(), settings.numeralMode)
        val monthLabel = PersianDateUtils.monthName(today.month, settings.languageTag)
        val weekdayLabel = PersianDateUtils.weekdayName(today.weekdayIndex, settings.languageTag)
        val badgeCount = if (tasksDueToday > 0) tasksDueToday else events.size

        return NotificationBoxContent(
            title = primaryDateTitle,
            moodSubtitle = moodSubtitle,
            secondaryDatesLine = secondaryDatesLine,
            collapsedLine = collapsedLine,
            occasionLines = occasionTitles,
            moreOccasionsCount = hiddenEvents,
            nextTaskSummary = nextTaskSummary,
            fallbackOccasionText = noOccasionText,
            fallbackTaskText = noTaskText,
            expandedText = expandedLines.joinToString("\n"),
            mood = mood,
            dayDigits = dayDigits,
            monthLabel = monthLabel,
            weekdayLabel = weekdayLabel,
            badgeCount = badgeCount,
        )
    }

    private fun updateInternal(
        context: Context,
        settings: AppSettings,
        tasks: List<Task>,
        catalog: OccasionCatalog,
        nowMillis: Long,
    ) {
        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        NotificationChannels.create(notificationContext)
        val content = buildContent(notificationContext, settings, tasks, catalog, nowMillis)
        val signature = listOf(
            content.title,
            content.moodSubtitle,
            content.collapsedLine,
            content.expandedText,
            content.dayDigits,
            content.monthLabel,
            content.mood.name,
            settings.statusBarIconStyle.name,
            content.badgeCount,
        ).hashCode()
        if (signature == lastPostedSignature) return

        val calendarIntent = PendingIntent.getActivity(
            notificationContext,
            NOTIFICATION_ID,
            Intent(notificationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("marbledo://calendar/today")
                putExtra(MainActivity.EXTRA_DESTINATION, "calendar")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val tasksIntent = PendingIntent.getActivity(
            notificationContext,
            NOTIFICATION_ID + 1,
            Intent(notificationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("marbledo://home/tasks")
                putExtra(MainActivity.EXTRA_DESTINATION, "home")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val accent = NotificationArtwork.accentColor(notificationContext, content.mood)
        val largeIcon = NotificationArtwork.dateBadge(notificationContext, content.dayDigits, content.monthLabel, content.mood)
            ?: ContextCompat.getDrawable(notificationContext, R.drawable.ic_notification_calendar_large)?.toBitmap()

        val builder = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_DATE)
            .setSmallIcon(NotificationArtwork.smallIcon(notificationContext, content.dayDigits, content.mood, settings.statusBarIconStyle))
            .setLargeIcon(largeIcon)
            .setColor(accent)
            .setContentTitle(content.title)
            .setContentText(content.collapsedLine)
            .setSubText(content.moodSubtitle)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content.expandedText)
                    .setBigContentTitle(content.title)
                    .setSummaryText(content.moodSubtitle),
            )
            .setContentIntent(calendarIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_stat_calendar,
                notificationContext.getString(R.string.calendar_notification_open),
                calendarIntent,
            )
            .addAction(
                R.drawable.ic_stat_marble,
                notificationContext.getString(R.string.persistent_date_notification_open_tasks),
                tasksIntent,
            )
        if (content.badgeCount > 0) {
            builder.setNumber(content.badgeCount)
        }
        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(notificationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        runCatching {
            NotificationManagerCompat.from(notificationContext).notify(NOTIFICATION_ID, notification)
            lastPostedSignature = signature
        }
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

    private fun nextDatedTask(tasks: List<Task>, nowMillis: Long): Task? {
        val active = tasks.filter { !it.isCompleted && !it.isArchived && it.dueAtEpochMillis != null }
        return active.asSequence()
            .filter { (it.dueAtEpochMillis ?: Long.MIN_VALUE) >= nowMillis }
            .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
            ?: active.maxByOrNull { it.dueAtEpochMillis ?: Long.MIN_VALUE }
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

    private const val MAX_VISIBLE_EVENTS = 3
}
