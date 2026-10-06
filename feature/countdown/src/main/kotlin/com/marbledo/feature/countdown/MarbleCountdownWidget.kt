package com.marbledo.feature.countdown

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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.util.TextNormalizer
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MarbleCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository(context).settings.first()
        val widgetContext = context.forLanguage(settings.languageTag)
        val task = withContext(Dispatchers.IO) {
            val database = MarbleDatabase.create(context)
            try {
                RoomTaskRepository(database.taskDao()).snapshot()
                    .firstOrNull { !it.isCompleted && !it.isArchived && it.dueAtEpochMillis != null }
            } finally {
                database.close()
            }
        }
        val remaining = task?.dueAtEpochMillis?.let { due ->
            val duration = Duration.ofMillis((due - System.currentTimeMillis()).coerceAtLeast(0))
            val days = duration.toDays()
            val hours = (duration.seconds / 3_600 % 24).toInt()
            widgetContext.getString(R.string.widget_time_left, days, hours)
                .let { TextNormalizer.formatDigits(it, settings.numeralMode) }
        }
        val title = task?.title ?: widgetContext.getString(R.string.widget_empty)
        val widgetName = widgetContext.getString(R.string.countdown_widget_name)
        // Glance has no Intent-based launch helper, so resolve the launcher ComponentName instead.
        val launchComponent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val openApp = launchComponent?.let { component -> actionStartActivity(component) }
        val contentModifier = GlanceModifier.fillMaxSize().padding(16.dp)
        val rootModifier = openApp?.let { action -> contentModifier.clickable(action) } ?: contentModifier

        provideContent {
            GlanceTheme {
                Column(
                    modifier = rootModifier,
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = widgetName,
                        style = TextStyle(color = ColorProvider(Color(0xFF6258C8)), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = title,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    remaining?.let {
                        Spacer(GlanceModifier.height(5.dp))
                        Text(
                            text = it,
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                        )
                    }
                }
            }
        }
    }
}

class MarbleCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarbleCountdownWidget()
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
