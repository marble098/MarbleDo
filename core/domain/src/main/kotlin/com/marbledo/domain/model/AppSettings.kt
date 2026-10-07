package com.marbledo.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode { AUTO, GLASS_LIGHT, DARK, AMOLED, DYNAMIC }

@Serializable
enum class NumeralMode { PERSIAN, LATIN, ARABIC }

@Serializable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
    val languageTag: String = "fa",
    val numeralMode: NumeralMode = NumeralMode.PERSIAN,
    val countdownTheme: String = "MARBLE",
    val countdownCalendar: CalendarDisplayMode = CalendarDisplayMode.PERSIAN,
    val taskCategories: List<String> = emptyList(),
    val weekStartsSaturday: Boolean = true,
    val calendarNotificationsEnabled: Boolean = true,
    val officialEventsEnabled: Boolean = true,
    val religiousEventsEnabled: Boolean = true,
    val nationalEventsEnabled: Boolean = true,
    val personalEventsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val fontScale: Float = 1f,
    val reduceMotion: Boolean = false,
    val lunarOffsetDays: Int = 0,
) {
    init {
        require(languageTag in setOf("fa", "en"))
        require(fontScale in 0.8f..1.5f)
        require(lunarOffsetDays in -2..2)
        require(quietStartMinute in 0..1439 && quietEndMinute in 0..1439)
    }
}
