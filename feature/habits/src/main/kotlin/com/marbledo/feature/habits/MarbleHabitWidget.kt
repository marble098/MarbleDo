package com.marbledo.feature.habits

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.RowScope
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomHabitRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitTheme
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.repository.HabitsSnapshot
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.TextNormalizer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How many habits fit on the widget before it offers the app instead of more rows. */
private const val MAX_WIDGET_ROWS = 4

private val HABIT_ID_KEY = ActionParameters.Key<Long>("habitId")

/**
 * A dedicated habit widget: today's list with a one-tap check-off, the day's progress and the best
 * run. Ticking a habit writes straight to the same database the app reads, so the widget and the
 * cabinet can never disagree.
 */
class MarbleHabitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widget updates arrive from launcher broadcasts and background jobs, so every data source
        // falls back to an empty value instead of letting an exception crash the process.
        val settings = runCatching {
            withContext(Dispatchers.IO) { SettingsRepository(context).settings.first() }
        }.getOrDefault(AppSettings())
        val widgetContext = context.forLanguage(settings.languageTag)
        val snapshot = runCatching {
            withContext(Dispatchers.IO) {
                val database = MarbleDatabase.create(context)
                RoomHabitRepository(database.habitDao()).snapshot()
            }
        }.getOrDefault(HabitsSnapshot())
        val today = LocalDate.now()
        val active = snapshot.habits.filter { !it.isArchived }
        val todays = active.filter { it.schedule.isScheduled(today) }
        val byHabit = snapshot.entries.groupBy { it.habitId }
        val done = todays.count { habit ->
            HabitAnalytics.isDone(habit, HabitAnalytics.valueOn(HabitAnalytics.entriesByDate(byHabit[habit.id].orEmpty()), today))
        }
        val bestStreak = active.maxOfOrNull { habit ->
            HabitAnalytics.stats(habit, byHabit[habit.id].orEmpty(), today).currentStreak
        } ?: 0
        val theme = HabitTheme.from(settings.habitTheme)
        val palette = habitPalette(theme, isDark(context))
        val numeral = settings.numeralMode
        // Glance lays out in the system direction, but the widget must follow the language the
        // user chose inside the app: Persian is right-to-left, English is left-to-right.
        val isRtl = settings.languageTag == "fa"
        val bodyAlignment = if (isRtl) TextAlign.End else TextAlign.Start
        val rows = todays.take(MAX_WIDGET_ROWS)
        val hidden = (todays.size - rows.size).coerceAtLeast(0)

        val launchComponent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val openApp = launchComponent?.let { component -> actionStartActivity(component) }
        val base = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.backgroundTop))
            .cornerRadius(26.dp)
            .padding(13.dp)
        val root = if (openApp != null) base.clickable(openApp) else base

        provideContent {
            GlanceTheme {
                Column(modifier = root) {
                    directionalRow(
                        isRtl = isRtl,
                        modifier = GlanceModifier.fillMaxWidth(),
                        items = listOf(
                            {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = widgetContext.getString(R.string.habit_widget_name),
                                        style = TextStyle(color = ColorProvider(palette.onCard), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = progressLine(widgetContext, done, todays.size, numeral),
                                        style = TextStyle(color = ColorProvider(palette.muted), fontSize = 10.sp, textAlign = bodyAlignment),
                                        maxLines = 1,
                                    )
                                }
                            },
                            {
                                if (bestStreak > 0) {
                                    Box(
                                        modifier = GlanceModifier
                                            .background(ColorProvider(palette.accentSoft))
                                            .cornerRadius(12.dp)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = widgetContext.getString(
                                                R.string.habit_widget_streak,
                                                TextNormalizer.formatDigits(bestStreak.toString(), numeral),
                                            ),
                                            style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                        ),
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    if (rows.isEmpty()) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(palette.card))
                                .cornerRadius(18.dp)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = widgetContext.getString(R.string.habit_widget_empty),
                                style = TextStyle(color = ColorProvider(palette.onCard), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = bodyAlignment),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Text(
                                text = widgetContext.getString(R.string.habit_widget_empty_body),
                                style = TextStyle(color = ColorProvider(palette.muted), fontSize = 11.sp, textAlign = bodyAlignment),
                                maxLines = 2,
                            )
                        }
                    } else {
                        rows.forEach { habit ->
                            WidgetHabitRow(
                                context = widgetContext,
                                habit = habit,
                                entries = byHabit[habit.id].orEmpty(),
                                today = today,
                                palette = palette,
                                numeral = numeral,
                                isRtl = isRtl,
                                align = bodyAlignment,
                            )
                            Spacer(GlanceModifier.height(6.dp))
                        }
                        if (hidden > 0) {
                            Text(
                                text = widgetContext.getString(
                                    R.string.habit_widget_progress,
                                    TextNormalizer.formatDigits(todays.size.toString(), numeral),
                                    TextNormalizer.formatDigits(active.size.toString(), numeral),
                                ),
                                style = TextStyle(color = ColorProvider(palette.muted), fontSize = 10.sp, textAlign = bodyAlignment),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetHabitRow(
        context: Context,
        habit: Habit,
        entries: List<HabitEntry>,
        today: LocalDate,
        palette: HabitPalette,
        numeral: NumeralMode,
        isRtl: Boolean,
        align: TextAlign,
    ) {
        val value = HabitAnalytics.valueOn(HabitAnalytics.entriesByDate(entries), today)
        val done = HabitAnalytics.isDone(habit, value)
        val isQuit = habit.kind == HabitKind.QUIT
        directionalRow(
            isRtl = isRtl,
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(palette.card))
                .cornerRadius(16.dp)
                .padding(horizontal = 9.dp, vertical = 7.dp),
            items = listOf(
                {
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .background(ColorProvider(palette.accentSoft))
                            .cornerRadius(11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(habit.icon, style = TextStyle(fontSize = 14.sp))
                    }
                },
                { Spacer(GlanceModifier.width(8.dp)) },
                {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = habit.name,
                            style = TextStyle(color = ColorProvider(palette.onCard), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = align),
                            maxLines = 1,
                        )
                        if (habit.tracking == com.marbledo.domain.model.HabitTracking.COUNT && !isQuit) {
                            Text(
                                text = TextNormalizer.formatDigits("$value/${habit.dailyTarget}", numeral),
                                style = TextStyle(color = ColorProvider(palette.muted), fontSize = 9.sp),
                                maxLines = 1,
                            )
                        }
                    }
                },
                { Spacer(GlanceModifier.width(6.dp)) },
                {
                    Box(
                        modifier = GlanceModifier
                            .size(30.dp)
                            .background(ColorProvider(if (isQuit && !done) palette.track else if (done) palette.accent else palette.track))
                            .cornerRadius(15.dp)
                            .clickable(
                                actionRunCallback<HabitToggleAction>(
                                    actionParametersOf(HABIT_ID_KEY to habit.id),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                isQuit && !done -> "!"
                                done -> "\u2713"
                                else -> ""
                            },
                            style = TextStyle(
                                color = ColorProvider(if (done) palette.backgroundTop else palette.onCard),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                },
            ),
        )
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

    private fun progressLine(
        context: Context,
        done: Int,
        scheduled: Int,
        numeral: NumeralMode,
    ): String {
        val doneText = TextNormalizer.formatDigits(done.toString(), numeral)
        val totalText = TextNormalizer.formatDigits(scheduled.toString(), numeral)
        return if (scheduled == 0) {
            context.getString(R.string.habit_summary_empty)
        } else if (done >= scheduled) {
            context.getString(R.string.habit_widget_all_done)
        } else {
            context.getString(R.string.habit_widget_progress, doneText, totalText)
        }
    }
}

/** Records today from the widget: tick, count-to-target, or note a slip on a quit habit. */
class HabitToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HABIT_ID_KEY] ?: return
        val today = LocalDate.now().toString()
        // A widget tap must never crash the app, even if the database is momentarily unavailable.
        runCatching {
            withContext(Dispatchers.IO) {
                val database = MarbleDatabase.create(context)
                val repository = RoomHabitRepository(database.habitDao())
                val snapshot = repository.snapshot()
                val habit = snapshot.habits.firstOrNull { it.id == habitId } ?: return@withContext
                val current = snapshot.entries.firstOrNull { it.habitId == habitId && it.date == today }?.value ?: 0
                val next = when (habit.kind) {
                    HabitKind.QUIT -> if (current > 0) 0 else 1
                    HabitKind.BUILD -> if (current >= habit.dailyTarget) 0 else habit.dailyTarget
                }
                repository.setEntry(habitId, today, next)
            }
            updateMarbleHabitWidgets(context)
        }
    }
}

class MarbleHabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarbleHabitWidget()
}

enum class HabitWidgetPinResult { REQUESTED, UNSUPPORTED, FAILED }

/** Asks the launcher to pin the habit widget; mirrors the countdown widget's flow. */
fun requestHabitWidgetPin(context: Context): HabitWidgetPinResult {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return HabitWidgetPinResult.UNSUPPORTED
    val accepted = runCatching {
        manager.requestPinAppWidget(ComponentName(context, MarbleHabitWidgetReceiver::class.java), null, null)
    }.getOrDefault(false)
    return if (accepted) HabitWidgetPinResult.REQUESTED else HabitWidgetPinResult.FAILED
}

/** Refreshes every pinned habit widget. A failed refresh is silent, never fatal. */
suspend fun updateMarbleHabitWidgets(context: Context) {
    runCatching {
        val appContext = context.applicationContext
        val ids = AppWidgetManager.getInstance(appContext).getAppWidgetIds(
            ComponentName(appContext, MarbleHabitWidgetReceiver::class.java),
        )
        if (ids.isEmpty()) return
        MarbleHabitWidget().updateAll(appContext)
    }
}

internal fun isDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

private fun Context.forLanguage(languageTag: String): Context {
    val locale = java.util.Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        @Suppress("DEPRECATION")
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
