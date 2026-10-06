package com.marbledo.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.NumeralMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<MarbleDoSettingsProto> by dataStore(
    fileName = "marbledo_settings.pb",
    serializer = SettingsSerializer,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsStore.data.map { proto -> proto.toDomain() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.updateData { old -> transform(old.toDomain()).toProto() }
    }

    private fun MarbleDoSettingsProto.toDomain(): AppSettings {
        val configured = hasExplicitSettings()
        return AppSettings(
            themeMode = enumOrDefault(themeMode, AppThemeMode.AUTO),
            languageTag = languageTag.takeIf { it in setOf("fa", "en") } ?: "fa",
            numeralMode = enumOrDefault(numeralMode, NumeralMode.PERSIAN),
            countdownTheme = countdownTheme.ifBlank { "MARBLE" },
            weekStartsSaturday = if (configured) weekStartsSaturday else true,
            calendarNotificationsEnabled = if (configured) calendarNotificationsEnabled else true,
            officialEventsEnabled = if (configured) officialEventsEnabled else true,
            religiousEventsEnabled = if (configured) religiousEventsEnabled else true,
            nationalEventsEnabled = if (configured) nationalEventsEnabled else true,
            personalEventsEnabled = if (configured) personalEventsEnabled else true,
            quietHoursEnabled = quietHoursEnabled,
            quietStartMinute = quietStartMinute.takeIf { it in 0..1439 } ?: 1320,
            quietEndMinute = quietEndMinute.takeIf { it in 0..1439 } ?: 420,
            fontScale = fontScale.takeIf { it in 0.8f..1.5f } ?: 1f,
            reduceMotion = reduceMotion,
            lunarOffsetDays = lunarOffsetDays.coerceIn(-2, 2),
        )
    }

    private fun AppSettings.toProto(): MarbleDoSettingsProto = MarbleDoSettingsProto.newBuilder()
        .setThemeMode(themeMode.name)
        .setLanguageTag(languageTag)
        .setNumeralMode(numeralMode.name)
        .setCountdownTheme(countdownTheme)
        .setWeekStartsSaturday(weekStartsSaturday)
        .setCalendarNotificationsEnabled(calendarNotificationsEnabled)
        .setOfficialEventsEnabled(officialEventsEnabled)
        .setReligiousEventsEnabled(religiousEventsEnabled)
        .setNationalEventsEnabled(nationalEventsEnabled)
        .setPersonalEventsEnabled(personalEventsEnabled)
        .setQuietHoursEnabled(quietHoursEnabled)
        .setQuietStartMinute(quietStartMinute)
        .setQuietEndMinute(quietEndMinute)
        .setFontScale(fontScale)
        .setReduceMotion(reduceMotion)
        .setLunarOffsetDays(lunarOffsetDays)
        .build()

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    // Proto3 scalar defaults cannot distinguish an unset false from an explicit false. Any persisted
    // profile has at least one of these non-empty fields, since every update writes the full profile.
    private fun MarbleDoSettingsProto.hasExplicitSettings(): Boolean =
        languageTag.isNotBlank() || themeMode.isNotBlank() || numeralMode.isNotBlank()
}
