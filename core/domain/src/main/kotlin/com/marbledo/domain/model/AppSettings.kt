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
    val countdownTheme: String = CountdownTheme.DEFAULT.id,
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
    /** When true, MarbleDo refreshes the occasion catalog from the internet in the background. */
    val occasionAutoUpdateEnabled: Boolean = true,
    /**
     * Shows today's date as a status-bar icon with a persistent, silent notification.
     * Enabled by default so the icon is visible without extra setup.
     */
    val persistentDateNotificationEnabled: Boolean = true,
    /** Calendar whose day number is drawn inside the status-bar date icon. */
    val statusBarCalendar: CalendarDisplayMode = CalendarDisplayMode.PERSIAN,
    /** Minutes before a task's due time that a heads-up reminder is shown; 0 disables it. */
    val reminderLeadMinutes: Int = 10,
) {
    init {
        require(languageTag in setOf("fa", "en"))
        require(fontScale in 0.8f..1.5f)
        require(lunarOffsetDays in -2..2)
        require(quietStartMinute in 0..1439 && quietEndMinute in 0..1439)
        require(reminderLeadMinutes in 0..1440)
    }
}
