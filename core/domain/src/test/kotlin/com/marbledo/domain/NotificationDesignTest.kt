package com.marbledo.domain

import com.marbledo.domain.model.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDesignTest {
    @Test
    fun `old backups default to classic`() {
        assertEquals("classic", Json.decodeFromString<AppSettings>("{}").notificationDesign)
    }

    @Test
    fun `all designs survive backup round trip`() {
        listOf("classic", "minimal", "editorial", "banner").forEach { design ->
            val settings = AppSettings(notificationDesign = design)
            assertEquals(settings, Json.decodeFromString<AppSettings>(Json.encodeToString(settings)))
        }
    }

    @Test
    fun `an old backup gets every channel switched on`() {
        // The per-channel switches are new; a profile saved before they existed must restore with
        // the app's defaults rather than with "everything off" (which is what a missing Boolean
        // without a default would have produced).
        val restored = Json.decodeFromString<AppSettings>("{}")
        assertTrue(restored.overdueNotificationsEnabled)
        assertTrue(restored.todayRemindersEnabled)
        assertEquals(8, restored.occasionNotificationHour)
    }

    @Test
    fun `the channel switches and the briefing hour survive a backup round trip`() {
        val settings = AppSettings(
            overdueNotificationsEnabled = false,
            todayRemindersEnabled = false,
            calendarNotificationsEnabled = false,
            occasionNotificationHour = 20,
        )
        val restored = Json.decodeFromString<AppSettings>(Json.encodeToString(settings))
        assertEquals(settings, restored)
        assertFalse(restored.overdueNotificationsEnabled)
        assertEquals(20, restored.occasionNotificationHour)
    }

    @Test
    fun `an hour outside the day is refused`() {
        assertThrows(IllegalArgumentException::class.java) { AppSettings(occasionNotificationHour = 24) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(occasionNotificationHour = -1) }
        // The edges are real hours and must stay legal.
        AppSettings(occasionNotificationHour = 0)
        AppSettings(occasionNotificationHour = 23)
    }
}
