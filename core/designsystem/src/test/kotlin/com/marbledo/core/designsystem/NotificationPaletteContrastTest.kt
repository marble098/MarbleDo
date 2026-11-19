package com.marbledo.core.designsystem

import com.marbledo.domain.model.AppThemeMode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contrast rules for the colours the notification card paints.
 *
 * These live here rather than in the app module because the palette is resolved from the design
 * system's own table, and because the app module cannot run these checks without an emulator. The
 * numbers are the ones `notificationPalette()` produces — the card cannot be seen in CI, so the
 * only way to catch "the overdue chip is written in a colour 1.3:1 against its own pill" is to
 * compute the same contrasts the card will have on screen.
 */
class NotificationPaletteContrastTest {

    // The same plates NotificationTheme paints, and the same hues it starts from.
    private val plates = listOf("light" to 0xFFF2F1F6L, "dark" to 0xFF26272EL)
    private val overdueHue = 0xFFC33A22L
    private val occasionHue = 0xFF6B4FA8L

    /** The accent of the *default* theme as the app actually resolves it, not a copy of it. */
    private fun accentOf(dark: Boolean): Long =
        MarblePalettes.resolve(AppThemeMode.AUTO.name, dark, null).primary

    private fun fills(plate: Long, accent: Long): List<Triple<String, Long, Long>> {
        val dark = MarbleContrast.relativeLuminance(plate) < 0.4
        val overdue = MarbleContrast.readable(overdueHue, plate)
        val occasion = MarbleContrast.readable(occasionHue, plate)
        return listOf(
            Triple("accent", accent, MarbleContrast.alphaOver(accent, plate, if (dark) 0.16 else 0.09)),
            Triple("occasion", occasion, MarbleContrast.alphaOver(occasion, plate, if (dark) 0.18 else 0.10)),
            Triple("overdue", overdue, MarbleContrast.blend(overdue, plate, 0.82)),
        )
    }

    @Test
    fun everyChipFillIsOpaque() {
        for ((plateName, plate) in plates) {
            val accent = accentOf(plateName == "dark")
            for ((name, _, fill) in fills(plate, accent)) {
                // A RemoteViews chip is a bitmap: a translucent fill would leave the card's plate
                // showing through wherever the pill does not cover it, and the ink could not be
                // checked against the pixels the reader sees.
                assertTrue(
                    ((fill ushr 24) and 0xFFL) == 0xFFL,
                    "$plateName $name fill is translucent: ${fill.toString(16)}",
                )
            }
        }
    }

    @Test
    fun theChipWordReadsOnTheFillBehindIt() {
        for ((plateName, plate) in plates) {
            val accent = accentOf(plateName == "dark")
            for ((name, _, fill) in fills(plate, accent)) {
                val ink = MarbleContrast.bestInk(fill)
                assertTrue(
                    MarbleContrast.ratio(ink, fill) >= MarbleContrast.TEXT_MINIMUM,
                    "$plateName $name chip: its word is only ${MarbleContrast.ratio(ink, fill)}:1 against its own pill",
                )
            }
        }
    }

    @Test
    fun theChipStateWordStillDiffersFromTheCardText() {
        for ((plateName, plate) in plates) {
            val accent = accentOf(plateName == "dark")
            for ((name, hue, fill) in fills(plate, accent)) {
                // The fill has to be a *shape*, not an invisible one: a pill the same colour as the
                // plate is a word floating on the card, which is what the old mood label was.
                assertTrue(
                    MarbleContrast.ratio(fill, plate) >= 1.12,
                    "$plateName $name fill is indistinguishable from the card (${MarbleContrast.ratio(fill, plate)}:1)",
                )
                assertTrue(
                    MarbleContrast.ratio(hue, plate) >= MarbleContrast.TEXT_MINIMUM,
                    "$plateName $name hue does not read on the card",
                )
            }
        }
    }
}
