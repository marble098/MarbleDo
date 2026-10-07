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
import com.marbledo.domain.util.TextNormalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A resizable, localized Glance card for the next active countdown. */
class MarbleCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = withContext(Dispatchers.IO) {
            SettingsRepository(context).settings.first()
        }
        val widgetContext = context.forLanguage(settings.languageTag)
        val task = withContext(Dispatchers.IO) {
            val database = MarbleDatabase.create(context)
            try {
                RoomTaskRepository(database.taskDao())
                    .snapshot()
                    .asSequence()
                    .filter { !it.isCompleted && !it.isArchived && it.countdownEnabled && it.dueAtEpochMillis != null }
                    .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
            } finally {
                database.close()
            }
        }

        val dueAt = task?.dueAtEpochMillis
        val targetDate = dueAt?.let {
            CountdownDateUtils.format(
                epochMillis = it,
                mode = settings.countdownCalendar,
                locale = widgetContext.resources.configuration.locales[0],
                numeralMode = settings.numeralMode,
            )
        }
        val remainingMillis = dueAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }
        val remainingSeconds = remainingMillis?.div(1_000L)
        val days = remainingSeconds?.div(86_400L)
        val hours = remainingSeconds?.div(3_600L)?.rem(24L)?.toInt()
        val minutes = remainingSeconds?.div(60L)?.rem(60L)?.toInt()
        val dayValue = days?.let { TextNormalizer.formatDigits(it.toString(), settings.numeralMode) }
        val hourValue = hours?.let { TextNormalizer.formatDigits(it.toString().padStart(2, '0'), settings.numeralMode) }
        val minuteValue = minutes?.let { TextNormalizer.formatDigits(it.toString().padStart(2, '0'), settings.numeralMode) }
        val themeId = task?.countdownTheme?.takeIf { CountdownTheme.isKnown(it) } ?: settings.countdownTheme
        val palette = widgetPalette(widgetContext, settings.themeMode, themeId)

        // The explicit component keeps the widget independent of app navigation internals.
        val launchComponent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val openApp = launchComponent?.let { component -> actionStartActivity(component) }
        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .cornerRadius(26.dp)
            .padding(15.dp)
        val rootModifier = if (openApp != null) baseModifier.clickable(openApp) else baseModifier

        provideContent {
            GlanceTheme {
                Column(
                    modifier = rootModifier,
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(36.dp)
                                .background(ColorProvider(palette.accentContainer))
                                .cornerRadius(12.dp),
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
                                text = widgetContext.getString(R.string.countdown_widget_name),
                                style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Text(
                                text = widgetContext.getString(R.string.widget_countdown_label),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(12.dp))
                    if (task == null) {
                        Text(
                            text = widgetContext.getString(R.string.widget_empty_title),
                            style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = widgetContext.getString(R.string.widget_empty),
                            style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 12.sp),
                            maxLines = 2,
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        Text(
                            text = widgetContext.getString(R.string.widget_open_app),
                            style = TextStyle(color = ColorProvider(palette.accent), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = task.title,
                            style = TextStyle(color = ColorProvider(palette.onBackground), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                        )
                        Spacer(GlanceModifier.height(10.dp))
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ColorProvider(palette.surface))
                                .cornerRadius(18.dp)
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            WidgetTimeUnit(
                                value = dayValue.orEmpty(),
                                label = widgetContext.getString(R.string.widget_unit_days),
                                palette = palette,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text("·", style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 18.sp))
                            WidgetTimeUnit(
                                value = hourValue.orEmpty(),
                                label = widgetContext.getString(R.string.widget_unit_hours),
                                palette = palette,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text("·", style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 18.sp))
                            WidgetTimeUnit(
                                value = minuteValue.orEmpty(),
                                label = widgetContext.getString(R.string.widget_unit_minutes),
                                palette = palette,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                        Spacer(GlanceModifier.height(8.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            val targetText = if (remainingMillis == 0L) {
                                widgetContext.getString(R.string.widget_due)
                            } else {
                                widgetContext.getString(R.string.widget_target_date, targetDate.orEmpty())
                            }
                            Text(
                                text = targetText,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 11.sp),
                                maxLines = 1,
                            )
                            Text(
                                text = widgetContext.getString(R.string.widget_open_app),
                                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
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
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = 21.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(palette.secondaryText), fontSize = 9.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

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
