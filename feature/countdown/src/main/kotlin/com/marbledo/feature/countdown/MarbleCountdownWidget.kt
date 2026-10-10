package com.marbledo.feature.countdown

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.feature.calendar.PersianDateUtils
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A resizable daily-planner widget: local dates, today’s occasions, next task and countdown. */
class MarbleCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = withContext(Dispatchers.IO) {
            SettingsRepository(context).settings.first()
        }
        val widgetContext = context.forLanguage(settings.languageTag)
        val tasks = withContext(Dispatchers.IO) {
            val database = MarbleDatabase.create(context)
            try {
                RoomTaskRepository(database.taskDao()).snapshot()
                    .filter { !it.isCompleted && !it.isArchived }
            } finally {
                database.close()
            }
        }
        val nowMillis = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val today = PersianDateUtils.today(zone, nowMillis)
        val catalog = withContext(Dispatchers.IO) {
            OccasionRepository(context).also { it.load() }.state.value.catalog
        }
        val enabledCategories = buildSet {
            if (settings.officialEventsEnabled) add(OccasionCategory.OFFICIAL)
            if (settings.nationalEventsEnabled) add(OccasionCategory.NATIONAL)
            if (settings.religiousEventsEnabled) add(OccasionCategory.RELIGIOUS)
            if (settings.personalEventsEnabled) add(OccasionCategory.PERSONAL)
            if (settings.internationalEventsEnabled) add(OccasionCategory.INTERNATIONAL)
        }
        val occasionIndex = OccasionIndex.build(
            catalog = catalog,
            jalaliYears = listOf(today.year),
            lunarOffsetDays = settings.lunarOffsetDays,
            enabledCategories = enabledCategories,
            zone = zone,
        )
        val todayOccasions = occasionIndex.on(today.year, today.month, today.day)
        val visibleOccasions = todayOccasions.take(2).map { it.title(settings.languageTag) }
        val remainingOccasions = todayOccasions.size - visibleOccasions.size
        val occasionSummary = when {
            visibleOccasions.isEmpty() -> widgetContext.getString(R.string.widget_no_occasions)
            remainingOccasions > 0 -> widgetContext.getString(
                R.string.widget_more_occasions,
                TextNormalizer.formatDigits(remainingOccasions.toString(), settings.numeralMode),
            ).let { visibleOccasions.joinToString(" · ") + " · " + it }
            else -> visibleOccasions.joinToString(" · ")
        }
        val dateSummary = PersianDateUtils.tripleDate(
            epochMillis = nowMillis,
            languageTag = settings.languageTag,
            numeralMode = settings.numeralMode,
            lunarOffsetDays = settings.lunarOffsetDays,
            locale = widgetContext.resources.configuration.locales[0],
            zone = zone,
        )
        val nextTask = nextWidgetTask(tasks, nowMillis)
        val countdownTask = tasks.asSequence()
            .filter { it.countdownEnabled && !it.isCompleted && !it.isArchived }
            .filter { (it.dueAtEpochMillis ?: Long.MIN_VALUE) >= nowMillis }
            .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
        val dueAt = countdownTask?.dueAtEpochMillis
        val targetDate = dueAt?.let {
            CountdownDateUtils.format(
                epochMillis = it,
                mode = settings.countdownCalendar,
                locale = widgetContext.resources.configuration.locales[0],
                numeralMode = settings.numeralMode,
            )
        }
        val remainingMillis = dueAt?.let { (it - nowMillis).coerceAtLeast(0L) }
        val remainingSeconds = remainingMillis?.div(1_000L)
        val days = remainingSeconds?.div(86_400L)
        val hours = remainingSeconds?.div(3_600L)?.rem(24L)?.toInt()
        val minutes = remainingSeconds?.div(60L)?.rem(60L)?.toInt()
        val dayValue = days?.let { TextNormalizer.formatDigits(it.toString(), settings.numeralMode) }
        val hourValue = hours?.let { TextNormalizer.formatDigits(it.toString().padStart(2, '0'), settings.numeralMode) }
        val minuteValue = minutes?.let { TextNormalizer.formatDigits(it.toString().padStart(2, '0'), settings.numeralMode) }
        val themeId = countdownTask?.countdownTheme?.takeIf { CountdownTheme.isKnown(it) } ?: settings.countdownTheme
        val palette = widgetPalette(widgetContext, settings.themeMode, themeId)

        // A tap anywhere on the glance card opens the calendar/planner in the app.
        val launchComponent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val openApp = launchComponent?.let { component -> actionStartActivity(component) }
        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .cornerRadius(28.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
        val rootModifier = if (openApp != null) baseModifier.clickable(openApp) else baseModifier

        provideContent {
            GlanceTheme {
                Column(
                    modifier = rootModifier,
                    verticalAlignment = Alignment.Vertical.Top,
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(38.dp)
                                .background(ColorProvider(palette.accentContainer))
                                .cornerRadius(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "◷",
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = widgetContext.getString(R.string.widget_today_label),
                                style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Text(
                                text = dateSummary,
                                style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 10.sp),
                                maxLines = 2,
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(10.dp))
                    GlanceInfoCard(
                        title = widgetContext.getString(R.string.widget_occasions_label),
                        body = occasionSummary,
                        palette = palette,
                    )
                    Spacer(GlanceModifier.height(7.dp))
                    GlanceInfoCard(
                        title = widgetContext.getString(R.string.widget_next_task_label),
                        body = nextTask?.title ?: widgetContext.getString(R.string.widget_no_next_task),
                        palette = palette,
                    )
                    Spacer(GlanceModifier.height(9.dp))

                    if (countdownTask == null) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(palette.accentContainer))
                                .cornerRadius(18.dp)
                                .padding(11.dp),
                        ) {
                            Text(
                                text = widgetContext.getString(R.string.widget_countdown_label),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Text(
                                text = widgetContext.getString(R.string.widget_no_countdown),
                                style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 11.sp),
                                maxLines = 2,
                            )
                        }
                    } else {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(palette.accentContainer))
                                .cornerRadius(18.dp)
                                .padding(11.dp),
                        ) {
                            Text(
                                text = widgetContext.getString(R.string.widget_countdown_label),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Text(
                                text = countdownTask.title,
                                style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(7.dp))
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(ColorProvider(palette.surface))
                                    .cornerRadius(14.dp)
                                    .padding(horizontal = 7.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                WidgetTimeUnit(dayValue.orEmpty(), widgetContext.getString(R.string.widget_unit_days), palette, GlanceModifier.defaultWeight())
                                Text("·", style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 16.sp))
                                WidgetTimeUnit(hourValue.orEmpty(), widgetContext.getString(R.string.widget_unit_hours), palette, GlanceModifier.defaultWeight())
                                Text("·", style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 16.sp))
                                WidgetTimeUnit(minuteValue.orEmpty(), widgetContext.getString(R.string.widget_unit_minutes), palette, GlanceModifier.defaultWeight())
                            }
                            Spacer(GlanceModifier.height(5.dp))
                            val targetText = if (remainingMillis == 0L) {
                                widgetContext.getString(R.string.widget_due)
                            } else {
                                widgetContext.getString(R.string.widget_target_date, targetDate.orEmpty())
                            }
                            Text(
                                text = targetText,
                                style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 10.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun GlanceInfoCard(title: String, body: String, palette: WidgetPalette) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(palette.surface))
                .cornerRadius(17.dp)
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = body,
                style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetTimeUnit(
        value: String,
        label: String,
        palette: WidgetPalette,
        modifier: GlanceModifier,
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 19.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 8.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** Next open task that is still ahead of [nowMillis]. Past or finished tasks are never shown here. */
private fun nextWidgetTask(tasks: List<Task>, nowMillis: Long): Task? =
    tasks.asSequence()
        .filter { !it.isCompleted && !it.isArchived }
        .filter { (it.dueAtEpochMillis ?: Long.MIN_VALUE) >= nowMillis }
        .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }

class MarbleCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarbleCountdownWidget()
}

enum class CountdownWidgetPinResult { REQUESTED, UNSUPPORTED, FAILED }

/** Request the platform launcher to pin the widget without leaking Glance classes to the app UI. */
fun requestCountdownWidgetPin(context: Context): CountdownWidgetPinResult {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return CountdownWidgetPinResult.UNSUPPORTED
    val accepted = runCatching {
        manager.requestPinAppWidget(
            ComponentName(context, MarbleCountdownWidgetReceiver::class.java),
            null,
            null,
        )
    }.getOrDefault(false)
    return if (accepted) CountdownWidgetPinResult.REQUESTED else CountdownWidgetPinResult.FAILED
}

/** Refresh all pinned countdown widgets after tasks or display preferences change. */
suspend fun updateMarbleCountdownWidgets(context: Context) {
    MarbleCountdownWidget().updateAll(context.applicationContext)
}

private data class WidgetPalette(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentContainer: Color,
)

private fun widgetPalette(context: Context, mode: AppThemeMode, themeId: String): WidgetPalette {
    val systemIsDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val isDark = when (mode) {
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.GLASS_LIGHT -> false
        AppThemeMode.AUTO, AppThemeMode.DYNAMIC -> systemIsDark
    }
    val accent = when (CountdownTheme.from(themeId)) {
        CountdownTheme.MARBLE_ORB -> Color(0xFF6258C8)
        CountdownTheme.AURORA_VEIL -> Color(0xFF32B8A6)
        CountdownTheme.NEON_GRID -> Color(0xFF00A6D6)
        CountdownTheme.FLIP_STUDIO -> Color(0xFFB86D36)
        CountdownTheme.GLASS_LAYERS -> Color(0xFF66899B)
        CountdownTheme.ORBITAL_RINGS -> Color(0xFF7665D7)
        CountdownTheme.TERMINAL_MATRIX -> Color(0xFF32B86C)
        CountdownTheme.LIQUID_TIDE -> Color(0xFF168F99)
        CountdownTheme.PAPER_TYPE -> Color(0xFF31566D)
        CountdownTheme.STELLAR_NIGHT -> Color(0xFF8A63D2)
        CountdownTheme.MOMENT_BLOCKS -> Color(0xFFDA6954)
        CountdownTheme.DIAL_GAUGE -> Color(0xFF287A91)
    }
    return WidgetPalette(
        background = when {
            mode == AppThemeMode.AMOLED -> Color.Black
            isDark -> Color(0xFF1D1A26)
            else -> Color(0xFFF7F5FC)
        },
        surface = if (isDark) Color(0xFF2A2634) else Color.White,
        onBackground = if (isDark) Color(0xFFF4F0FA) else Color(0xFF211D2A),
        secondaryText = if (isDark) Color(0xFFC0BBCB) else Color(0xFF6D6878),
        accent = accent,
        accentContainer = if (isDark) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.12f),
    )
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
