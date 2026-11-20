package com.marbledo.core.designsystem

import com.marbledo.domain.model.AppThemeMode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contrast rules for the colours the notification card paints.
 *
 * These live here rather than in the app module because the palette is resolved from the design
 * system's own table, and because the app module cannot run these checks without an emulator. The
 * numbers are the ones `notificationPalette()` and `NotificationChipFills` produce — the card cannot
 * be seen in CI, so the only way to catch "the overdue word is 1.3:1 against its own pill" is to
 * compute the same contrasts the card will have on screen.
 */
class NotificationPaletteContrastTest {

    // The two plates NotificationTheme can paint, and the hues it starts from. There is one surface
    // on the card — the plate — so there is nothing else for a colour to have to read against.
    private val plates = listOf(
        "light" to 0xFFF2F1F6L,
        "dark" to 0xFF26272EL,
    )
    private val overdueHue = 0xFFC33A22L
    private val occasionHue = 0xFF6B4FA8L

    // The literal pill fills of NotificationChipFills / drawable/notification_chip_*.xml.
    private val chipFills = mapOf(
        "light" to mapOf("overdue" to 0xFFF3D9D3L, "occasion" to 0xFFE2DAF1L, "neutral" to 0xFFE4E3E9L),
        "dark" to mapOf("overdue" to 0xFF4E2B26L, "occasion" to 0xFF3A3252L, "neutral" to 0xFF35363EL),
    )

    private fun resolved(dark: Boolean): MarblePalette = MarblePalettes.resolve(AppThemeMode.AUTO.name, dark, null)

    /** The same lift `notificationPalette` applies: readable on the plate. */
    private fun ink(colour: Long, plate: Long): Long = MarbleContrast.readable(colour, plate)

    private fun hues(plateName: String, plate: Long): Map<String, Long> {
        val app = resolved(plateName == "dark")
        return mapOf(
            "overdue" to ink(overdueHue, plate),
            "occasion" to ink(occasionHue, plate),
            "neutral" to ink(app.onSurfaceVariant, plate),
        )
    }

    @Test
    fun everyStateHueReadsOnThePlate() {
        for ((plateName, plate) in plates) {
            for ((name, hue) in hues(plateName, plate)) {
                assertTrue(MarbleContrast.ratio(hue, plate) >= MarbleContrast.TEXT_MINIMUM, "$plateName $name does not read on the card")
            }
            val app = resolved(plateName == "dark")
            for ((name, colour) in listOf("text" to app.onSurface, "muted" to app.onSurfaceVariant, "accent" to app.primary)) {
                val lifted = ink(colour, plate)
                assertTrue(MarbleContrast.ratio(lifted, plate) >= MarbleContrast.TEXT_MINIMUM, "$plateName $name does not read on the card")
            }
        }
    }

    @Test
    fun theStateWordReadsOnItsOwnPill() {
        for ((plateName, plate) in plates) {
            val stateHues = hues(plateName, plate)
            for ((name, fill) in chipFills.getValue(plateName)) {
                // The word on the pill is the state hue re-checked against the *pill*, the way
                // NotificationChipFills.ink does it; the hue alone is only guaranteed on the card.
                val ink = MarbleContrast.readable(stateHues.getValue(name), fill)
                assertTrue(
                    MarbleContrast.ratio(ink, fill) >= MarbleContrast.TEXT_MINIMUM,
                    "$plateName $name pill: its word is only ${MarbleContrast.ratio(ink, fill)}:1 against its own fill",
                )
                // The pill has to be a *shape* on the card, not an invisible one.
                assertTrue(
                    MarbleContrast.ratio(fill, plate) >= 1.12,
                    "$plateName $name pill is indistinguishable from the card (${MarbleContrast.ratio(fill, plate)}:1)",
                )
            }
        }
    }

    @Test
    fun theBandInkReadsOnEveryThemeAccent() {
        // The banner paints its header in the theme accent and writes the date in bestInk(accent);
        // every selectable theme, light and dark, has to clear the text bar on its own band.
        for (mode in AppThemeMode.entries) {
            for (dark in listOf(false, true)) {
                val plate = if (dark) 0xFF26272EL else 0xFFF2F1F6L
                val accent = ink(MarblePalettes.resolve(mode.name, dark, null).primary, plate)
                val onAccent = MarbleContrast.bestInk(accent)
                assertTrue(
                    MarbleContrast.ratio(onAccent, accent) >= MarbleContrast.TEXT_MINIMUM,
                    "$mode (dark=$dark): the band ink is only ${MarbleContrast.ratio(onAccent, accent)}:1 on the band",
                )
            }
        }
    }
}
