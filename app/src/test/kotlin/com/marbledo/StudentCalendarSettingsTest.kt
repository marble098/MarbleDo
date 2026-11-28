package com.marbledo

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Keep the optional school calendar discoverable without requiring an account or hidden gesture. */
class StudentCalendarSettingsTest {

    @Test
    fun `school calendar is a visible one tap calendar setting`() {
        val source = settingsScreen()
        val calendarSection = source
            .substringAfter("SettingsSection(\n            title = stringResource(R.string.settings_calendar)")
            .substringBefore("SettingsSection(title = stringResource(R.string.settings_occasion_sources)")

        assertTrue(calendarSection.contains("text = stringResource(R.string.settings_education_mode)"))
        assertTrue(calendarSection.contains("subtitle = stringResource(R.string.settings_education_hint)"))
        assertTrue(calendarSection.contains("checked = settings.educationModeEnabled"))
        assertTrue(calendarSection.contains("educationModeEnabled = it"))
        assertFalse(calendarSection.contains("if (settings.educationModeEnabled)"))
        assertFalse(source.contains("HiddenTapSequence"), "students should not need a secret tap sequence")
    }

    private fun settingsScreen(): String = listOf(
        File("src/main/kotlin/com/marbledo/SettingsScreen.kt"),
        File("../app/src/main/kotlin/com/marbledo/SettingsScreen.kt"),
    ).firstOrNull(File::exists)?.readText(Charsets.UTF_8)
        ?: error("SettingsScreen.kt not found")
}
