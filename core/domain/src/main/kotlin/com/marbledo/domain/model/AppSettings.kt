package com.marbledo.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode { AUTO, GLASS_LIGHT, DARK, AMOLED, DYNAMIC, OCEAN, FOREST, SUNSET, ROSE }

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
    val officialEventsEnabled: Boolean = true,
    val religiousEventsEnabled: Boolean = true,
    val nationalEventsEnabled: Boolean = true,
    val personalEventsEnabled: Boolean = true,
    val internationalEventsEnabled: Boolean = true,
    /** Opt-in school calendar, hidden until explicitly activated on this device. */
    val educationModeEnabled: Boolean = false,
    /** Friday is always excluded by the workday helper; optionally exclude Thursday too. */
    val thursdayWeekendEnabled: Boolean = false,
    val fontScale: Float = 1f,
    val reduceMotion: Boolean = false,
    val lunarOffsetDays: Int = 0,
    /** When true, MarbleDo refreshes the occasion catalog from the internet in the background. */
    val occasionAutoUpdateEnabled: Boolean = true,
    /** Look of the habit cabinet; individual habits can carry their own look. */
    val habitTheme: String = HabitTheme.DEFAULT.id,
    /** Occasion ids the user marked as important, so they are pinned. */
    val favoriteOccasionIds: List<String> = emptyList(),
    /** How the dashboard lays out its task and countdown sections; see [DashboardViewMode]. */
    val dashboardViewMode: DashboardViewMode = DashboardViewMode.LIST,
    /** Independent discovery packs; an empty list intentionally hides all occasion packs. */
    val occasionCultureIds: List<String> = OccasionCulture.defaultIds,
) {
    init {
        require(languageTag in setOf("fa", "en"))
        require(fontScale in 0.8f..1.5f)
        require(lunarOffsetDays in -2..2)
    }
}
