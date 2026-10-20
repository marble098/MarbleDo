package com.marbledo.domain

import com.marbledo.domain.model.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationDesignTest {
    @Test
    fun `old backups default to classic`() {
        assertEquals("classic", Json.decodeFromString<AppSettings>("{}").notificationDesign)
    }

    @Test
    fun `all designs survive backup round trip`() {
        listOf("classic", "minimal", "editorial").forEach { design ->
            val settings = AppSettings(notificationDesign = design)
            assertEquals(settings, Json.decodeFromString<AppSettings>(Json.encodeToString(settings)))
        }
    }
}
