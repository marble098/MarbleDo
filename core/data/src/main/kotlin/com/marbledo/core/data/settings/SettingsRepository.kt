package com.marbledo.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<MarbleDoSettingsProto> by dataStore(
    fileName = "marbledo_settings.pb",
    serializer = SettingsSerializer,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsStore.data.map { proto -> toDomain(proto) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.updateData { old -> toProto(transform(toDomain(old))) }
    }

    private fun toDomain(proto: MarbleDoSettingsProto): AppSettings {
        val configured = hasExplicitSettings(proto)
        return AppSettings(
            themeMode = enumOrDefault(proto.themeMode, AppThemeMode.AUTO),
            languageTag = proto.languageTag.takeIf { it in setOf("fa", "en") } ?: "fa",
            numeralMode = enumOrDefault(proto.numeralMode, NumeralMode.PERSIAN),
            countdownTheme = proto.countdownTheme.ifBlank { CountdownTheme.DEFAULT.id },
            countdownCalendar = enumOrDefault(proto.countdownCalendar, CalendarDisplayMode.PERSIAN),
            taskCategories = proto.taskCategoriesList.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            weekStartsSaturday = if (configured) proto.weekStartsSaturday else true,
            calendarNotificationsEnabled = if (configured) proto.calendarNotificationsEnabled else true,
            officialEventsEnabled = if (configured) proto.officialEventsEnabled else true,
            religiousEventsEnabled = if (configured) proto.religiousEventsEnabled else true,
            nationalEventsEnabled = if (configured) proto.nationalEventsEnabled else true,
            personalEventsEnabled = if (configured) proto.personalEventsEnabled else true,
            quietHoursEnabled = proto.quietHoursEnabled,
            quietStartMinute = proto.quietStartMinute.takeIf { it in 0..1439 } ?: 1320,
            quietEndMinute = proto.quietEndMinute.takeIf { it in 0..1439 } ?: 420,
            fontScale = proto.fontScale.takeIf { it in 0.8f..1.5f } ?: 1f,
            reduceMotion = proto.reduceMotion,
            lunarOffsetDays = proto.lunarOffsetDays.coerceIn(-2, 2),
            occasionAutoUpdateEnabled = if (configured) proto.occasionAutoUpdateEnabled else true,
        )
    }

    private fun toProto(settings: AppSettings): MarbleDoSettingsProto = MarbleDoSettingsProto.newBuilder()
        .setThemeMode(settings.themeMode.name)
        .setLanguageTag(settings.languageTag)
        .setNumeralMode(settings.numeralMode.name)
        .setCountdownTheme(settings.countdownTheme)
        .setCountdownCalendar(settings.countdownCalendar.name)
        .addAllTaskCategories(settings.taskCategories.map { it.trim() }.filter { it.isNotBlank() }.distinct())
        .setWeekStartsSaturday(settings.weekStartsSaturday)
        .setCalendarNotificationsEnabled(settings.calendarNotificationsEnabled)
        .setOfficialEventsEnabled(settings.officialEventsEnabled)
        .setReligiousEventsEnabled(settings.religiousEventsEnabled)
        .setNationalEventsEnabled(settings.nationalEventsEnabled)
        .setPersonalEventsEnabled(settings.personalEventsEnabled)
        .setQuietHoursEnabled(settings.quietHoursEnabled)
        .setQuietStartMinute(settings.quietStartMinute)
        .setQuietEndMinute(settings.quietEndMinute)
        .setFontScale(settings.fontScale)
        .setReduceMotion(settings.reduceMotion)
        .setLunarOffsetDays(settings.lunarOffsetDays)
        .setOccasionAutoUpdateEnabled(settings.occasionAutoUpdateEnabled)
        .build()

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    // Proto3 scalar defaults cannot distinguish an unset false from an explicit false. Any persisted
    // profile has at least one of these non-empty fields, since every update writes the full profile.
    private fun hasExplicitSettings(proto: MarbleDoSettingsProto): Boolean =
        proto.languageTag.isNotBlank() || proto.themeMode.isNotBlank() || proto.numeralMode.isNotBlank()
}
