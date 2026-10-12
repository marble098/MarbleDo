package com.marbledo.domain

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.StatusBarIconStyle
import com.marbledo.domain.model.StatusBarIconStyleSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusBarIconStyleTest {
    @Test
    fun `persisted style ids stay stable across releases`() {
        assertEquals(
            listOf("NUMERAL", "FRAME", "TILE", "DISC", "PAGE", "ICON_ONLY"),
            StatusBarIconStyle.entries.map { it.name },
        )
    }

    @Test
    fun `every style round trips through the id it is saved with`() {
        StatusBarIconStyle.entries.forEach { style ->
            assertEquals(style, StatusBarIconStyle.from(style.name))
            assertTrue(StatusBarIconStyle.isKnown(style.name))
        }
    }

    @Test
    fun `ids from the first generation of looks still resolve`() {
        // Installs that saved "BOLD_DIGITS" must not silently lose the day number from their icon.
        assertEquals(StatusBarIconStyle.NUMERAL, StatusBarIconStyle.from("BOLD_DIGITS"))
        assertEquals(StatusBarIconStyle.PAGE, StatusBarIconStyle.from("CALENDAR_PAGE"))
        assertEquals(StatusBarIconStyle.ICON_ONLY, StatusBarIconStyle.from("ICON_ONLY"))
    }

    @Test
    fun `a style always encodes as its persisted name`() {
        assertEquals("\"TILE\"", Json.encodeToString(StatusBarIconStyleSerializer, StatusBarIconStyle.TILE))
        assertEquals("\"ICON_ONLY\"", Json.encodeToString(StatusBarIconStyleSerializer, StatusBarIconStyle.ICON_ONLY))
    }

    @Test
    fun `settings saved by the release that shipped the first looks still load`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
        val bold = json.decodeFromString<AppSettings>("""{"languageTag":"fa","statusBarIconStyle":"BOLD_DIGITS"}""")
        assertEquals(StatusBarIconStyle.NUMERAL, bold.statusBarIconStyle)
        val page = json.decodeFromString<AppSettings>("""{"languageTag":"fa","statusBarIconStyle":"CALENDAR_PAGE"}""")
        assertEquals(StatusBarIconStyle.PAGE, page.statusBarIconStyle)
    }

    @Test
    fun `unknown or empty ids fall back to the default look`() {
        assertEquals(StatusBarIconStyle.DEFAULT, StatusBarIconStyle.from(""))
        assertEquals(StatusBarIconStyle.DEFAULT, StatusBarIconStyle.from("MARBLE_GLOW"))
        assertEquals(StatusBarIconStyle.NUMERAL, StatusBarIconStyle.DEFAULT)
    }
}
