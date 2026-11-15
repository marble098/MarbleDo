package com.marbledo.feature.countdown

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.core.designsystem.MarblePalettes
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.enabledOccasionCultures
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.calendar.enabledOccasionCategories
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The daily-planner widget, third generation.
 *
 * The previous card stacked four boxes — a header, an occasions card, a next-task card and a
 * countdown card inside the countdown card — so a glance had to parse six labels before it found
 * the one number it came for. The redesign keeps three bands and one filled surface:
 *
 *  1. **the date**, with the two other calendars as one quiet line;
 *  2. **the countdown**, the only card, which is the reason this widget is pinned;
 *  3. **two meta lines** — today's occasions and the next task — as sentences, not cards.
 *
 * Everything still follows the app theme and the app language, not the device's.
 */
class MarbleCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widget updates arrive from launcher broadcasts and background jobs, so every data source
        // falls back to an empty value instead of letting an exception crash the process.
        val settings = runCatching {
            withContext(Dispatchers.IO) { SettingsRepository(context).settings.first() }
        }.getOrDefault(AppSettings())
        val widgetContext = context.forLanguage(settings.languageTag)
        val tasks = runCatching {
            withContext(Dispatchers.IO) {
                val database = MarbleDatabase.create(context)
                RoomTaskRepository(database.taskDao()).snapshot()
                    .filter { !it.isCompleted && !it.isArchived }
            }
        }.getOrDefault(emptyList())
        val nowMillis = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val today = PersianDateUtils.today(zone, nowMillis)
        val catalog = runCatching {
            withContext(Dispatchers.IO) {
                OccasionRepository(context).also { it.load() }.state.value.catalog
            }
        }.getOrDefault(OccasionCatalog.EMPTY)
        val enabledCategories = settings.enabledOccasionCategories()
        val occasionIndex = runCatching {
            OccasionIndex.build(
                catalog = catalog,
                jalaliYears = listOf(today.year),
                lunarOffsetDays = settings.lunarOffsetDays,
                enabledCategories = enabledCategories,
                enabledCultures = settings.enabledOccasionCultures(),
                zone = zone,
            )
        }.getOrDefault(OccasionIndex.EMPTY)
        val todayOccasions = occasionIndex.on(today.year, today.month, today.day)
        val visibleOccasions = todayOccasions.take(MAX_OCCASION_NAMES).map { it.title(settings.languageTag) }
        val remainingOccasions = todayOccasions.size - visibleOccasions.size
        val occasionSummary = when {
            visibleOccasions.isEmpty() -> widgetContext.getString(R.string.widget_no_occasions)
            remainingOccasions > 0 -> visibleOccasions.joinToString(" · ") + " · " +
                widgetContext.getString(
                    R.string.widget_more_occasions,
                    TextNormalizer.formatDigits(remainingOccasions.toString(), settings.numeralMode),
                )
            else -> visibleOccasions.joinToString(" · ")
        }
        val deviceLocale = firstLocale(widgetContext)
        val dateSummary = PersianDateUtils.tripleDate(
            epochMillis = nowMillis,
            languageTag = settings.languageTag,
            numeralMode = settings.numeralMode,
            lunarOffsetDays = settings.lunarOffsetDays,
            locale = deviceLocale,
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
                locale = deviceLocale,
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
        // The widget colours come from the app theme, exactly like the screens: a theme switch now
        // repaints the home-screen card instead of leaving it on the old indigo.
        val palette = widgetPalette(widgetContext, settings.themeMode)
        // Glance lays out in the system direction, but the widget must follow the language the
        // user chose inside the app: Persian is right-to-left, English is left-to-right.
        val isRtl = settings.languageTag == "fa"
        val bodyAlignment = if (isRtl) TextAlign.End else TextAlign.Start

        // A tap anywhere on the glance card opens the calendar/planner in the app.
        val launchComponent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val openApp = launchComponent?.let { component -> actionStartActivity(component) }
        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .cornerRadius(28.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
        val rootModifier = if (openApp != null) baseModifier.clickable(openApp) else baseModifier
        val metaNext = nextTask?.title?.let { widgetContext.getString(R.string.widget_meta_next, it) }
            ?: widgetContext.getString(R.string.widget_no_next_task)
        val metaOccasions = if (todayOccasions.isEmpty()) {
            widgetContext.getString(R.string.widget_no_occasions)
        } else {
            widgetContext.getString(R.string.widget_meta_occasions, occasionSummary)
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = rootModifier,
                    verticalAlignment = Alignment.Vertical.Top,
                ) {
                    // 1. The date, and nothing else, at the top.
                    Text(
                        text = widgetContext.getString(R.string.widget_today_label),
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(
                            color = ColorProvider(palette.accent),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = bodyAlignment,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = dateSummary,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(
                            color = ColorProvider(palette.onBackground),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = bodyAlignment,
                        ),
                        maxLines = 2,
                    )
                    Spacer(GlanceModifier.height(9.dp))
                    // A hairline instead of a card: one line of the drawing, no surface.
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorProvider(palette.secondaryText.copy(alpha = 0.22f))),
                    ) { }
                    Spacer(GlanceModifier.height(9.dp))

                    // 2. The countdown: the one filled surface of the widget.
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ColorProvider(palette.surface))
                            .cornerRadius(22.dp)
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                    ) {
                        directionalRow(
                            isRtl = isRtl,
                            modifier = GlanceModifier.fillMaxWidth(),
                            items = listOf(
                                {
                                    Text(
                                        text = widgetContext.getString(R.string.widget_countdown_label),
                                        modifier = GlanceModifier.defaultWeight(),
                                        style = TextStyle(
                                            color = ColorProvider(palette.accent),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = bodyAlignment,
                                        ),
                                        maxLines = 1,
                                    )
                                },
                                {
                                    Text(
                                        text = if (countdownTask == null) "" else if (remainingMillis == 0L) {
                                            widgetContext.getString(R.string.widget_due)
                                        } else {
                                            widgetContext.getString(R.string.widget_target_date, targetDate.orEmpty())
                                        },
                                        style = TextStyle(
                                            color = ColorProvider(palette.secondaryText),
                                            fontSize = 9.sp,
                                            textAlign = bodyAlignment,
                                        ),
                                        maxLines = 1,
                                    )
                                },
                            ),
                        )
                        if (countdownTask == null) {
                            Spacer(GlanceModifier.height(3.dp))
                            Text(
                                text = widgetContext.getString(R.string.widget_empty),
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = ColorProvider(palette.onBackground),
                                    fontSize = 12.sp,
                                    textAlign = bodyAlignment,
                                ),
                                maxLines = 2,
                            )
                        } else {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = countdownTask.title,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = ColorProvider(palette.onBackground),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = bodyAlignment,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(8.dp))
                            // Three readings, one line: the day is large, hours and minutes are a
                            // footnote to it, and the units sit under their own number once.
                            directionalRow(
                                isRtl = isRtl,
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Vertical.Bottom,
                                items = listOf(
                                    {
                                        WidgetTimeUnit(
                                            value = dayValue.orEmpty(),
                                            label = widgetContext.getString(R.string.widget_unit_days),
                                            palette = palette,
                                            valueSize = 30,
                                            modifier = GlanceModifier.defaultWeight(),
                                        )
                                    },
                                    {
                                        WidgetTimeUnit(
                                            value = hourValue.orEmpty(),
                                            label = widgetContext.getString(R.string.widget_unit_hours),
                                            palette = palette,
                                            valueSize = 18,
                                            modifier = GlanceModifier.defaultWeight(),
                                        )
                                    },
                                    {
                                        WidgetTimeUnit(
                                            value = minuteValue.orEmpty(),
                                            label = widgetContext.getString(R.string.widget_unit_minutes),
                                            palette = palette,
                                            valueSize = 18,
                                            modifier = GlanceModifier.defaultWeight(),
                                        )
                                    },
                                ),
                            )
                        }
                    }

                    // 3. Two quiet lines: what the day carries and what comes next.
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = metaOccasions,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(
                            color = ColorProvider(palette.secondaryText),
                            fontSize = 10.sp,
                            textAlign = bodyAlignment,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text = metaNext,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(
                            color = ColorProvider(palette.onBackground),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = bodyAlignment,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    /**
     * Glance lays rows out in the device direction only, so when the app language is Persian the
     * children are emitted in reverse order to mirror the in-app RTL layout; English keeps the
     * natural left-to-right order.
     */
    @androidx.compose.runtime.Composable
    private fun directionalRow(
        isRtl: Boolean,
        modifier: GlanceModifier = GlanceModifier,
        verticalAlignment: Alignment.Vertical = Alignment.Vertical.CenterVertically,
        items: List<@Composable RowScope.() -> Unit>,
    ) {
        Row(modifier = modifier, verticalAlignment = verticalAlignment) {
            (if (isRtl) items.asReversed() else items).forEach { item -> item() }
        }
    }

    /**
     * One number with its unit beneath it. The label is deliberately part of the same column so a
     * reading cannot drift away from the word that says what it counts.
     */
    @androidx.compose.runtime.Composable
    private fun WidgetTimeUnit(
        value: String,
        label: String,
        palette: WidgetPalette,
        valueSize: Int,
        modifier: GlanceModifier,
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = value,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = label,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(palette.secondaryText),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/** How many occasion names fit the quiet occasion line before it counts the rest. */
private const val MAX_OCCASION_NAMES = 2

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

/** Refreshes every pinned countdown widget. A failed refresh is silent, never fatal. */
suspend fun updateMarbleCountdownWidgets(context: Context) {
    runCatching {
        val appContext = context.applicationContext
        val ids = AppWidgetManager.getInstance(appContext).getAppWidgetIds(
            ComponentName(appContext, MarbleCountdownWidgetReceiver::class.java),
        )
        if (ids.isEmpty()) return
        MarbleCountdownWidget().updateAll(appContext)
    }
}

private data class WidgetPalette(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentContainer: Color,
)

private fun widgetPalette(context: Context, mode: AppThemeMode): WidgetPalette {
    val systemIsDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val dark = MarblePalettes.isDark(mode.name, systemIsDark)
    val theme = MarblePalettes.resolve(mode, dark, context.applicationContext)
    return WidgetPalette(
        background = Color(theme.background),
        surface = Color(theme.surfaceContainer),
        onBackground = Color(theme.onBackground),
        secondaryText = Color(theme.onSurfaceVariant),
        accent = Color(theme.primary),
        accentContainer = Color(theme.primaryContainer),
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

/** The primary locale, falling back to a sensible default instead of crashing on an empty list. */
private fun firstLocale(context: Context): Locale {
    val locales = context.resources.configuration.locales
    return if (!locales.isEmpty) locales[0] else Locale.forLanguageTag("fa")
}
