package com.marbledo.core.designsystem

import com.marbledo.domain.model.AppThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contrast maths that keeps the parts painted outside Compose readable.
 *
 * The screens get their text colours from Material, which guarantees the pairs. The notification
 * card and the widget plates do not: they mix a colour from the app theme with a background that
 * may come from somewhere else, and that is where unreadable cards came from. These tests pin the
 * two things that used to break — the WCAG arithmetic itself, and the guarantee that every theme's
 * text survives on that theme's own plate.
 */
class MarbleContrastTest {

    @Test
    fun theScaleIsTheWcagOne() {
        assertEquals(21.0, MarbleContrast.ratio(0xFF000000L, 0xFFFFFFFFL), 0.001)
        assertEquals(1.0, MarbleContrast.ratio(0xFF777777L, 0xFF777777L), 0.001)
        // 0x767676 on white is the classic "just passes AA" grey, at 4.54:1.
        assertEquals(4.54, MarbleContrast.ratio(0xFF767676L, 0xFFFFFFFFL), 0.02)
    }

    @Test
    fun readableKeepsAColourThatAlreadyPasses() {
        val ink = 0xFF1C1B22L
        val plate = 0xFFF2F1F6L
        assertTrue(MarbleContrast.ratio(ink, plate) >= MarbleContrast.TEXT_MINIMUM)
        assertEquals(ink, MarbleContrast.readable(ink, plate))
    }

    @Test
    fun readableLiftsAColourThatDoesNot() {
        // A pale accent on a pale plate: the exact failure the notification card had.
        val pale = 0xFFBBC5FFL
        val plate = 0xFFF2F1F6L
        assertTrue(MarbleContrast.ratio(pale, plate) < MarbleContrast.TEXT_MINIMUM)
        val fixed = MarbleContrast.readable(pale, plate)
        assertTrue(
            MarbleContrast.ratio(fixed, plate) >= MarbleContrast.TEXT_MINIMUM,
            "readable() returned a colour that still fails: ${fixed.toString(16)}",
        )
    }

    @Test
    fun readableLiftsADarkColourOnADarkPlate() {
        val deep = 0xFF111E58L
        val plate = 0xFF0C0C11L
        assertTrue(MarbleContrast.ratio(deep, plate) < MarbleContrast.TEXT_MINIMUM)
        val fixed = MarbleContrast.readable(deep, plate)
        assertTrue(MarbleContrast.ratio(fixed, plate) >= MarbleContrast.TEXT_MINIMUM)
    }

    @Test
    fun bestInkPicksTheSideThatReads() {
        assertEquals(MarbleContrast.INK_DARK, MarbleContrast.bestInk(0xFFFFFBFEL))
        assertEquals(MarbleContrast.INK_LIGHT, MarbleContrast.bestInk(0xFF141218L))
    }

    @Test
    fun blendEndsAreExact() {
        val a = 0xFF4355A5L
        val b = 0xFFF2F1F6L
        assertEquals(a or 0xFF000000L, MarbleContrast.blend(a, b, 0.0))
        assertEquals(b or 0xFF000000L, MarbleContrast.blend(a, b, 1.0))
        val middle = MarbleContrast.blend(0xFF000000L, 0xFFFFFFFFL, 0.5)
        assertEquals(0xFF808080L, middle)
    }

    @Test
    fun withAlphaOnlyTouchesTheAlphaByte() {
        val tinted = MarbleContrast.withAlpha(0xFF4355A5L, 0.5)
        assertEquals(0x80, (tinted ushr 24) and 0xFF)
        assertEquals(0x4355A5L, tinted and 0x00FFFFFFL)
    }

    @Test
    fun widenedIntsRoundTrip() {
        val value = 0xFFBBC5FF.toInt()
        assertEquals(0xFFBBC5FFL, value.toArgb())
        assertEquals(value, value.toArgb().toArgbInt())
    }

    @Test
    fun everyThemesInkReadsOnItsOwnPlate() {
        // The notification card paints `surface` and states `onSurface`, `onSurfaceVariant` and
        // `primary` on top of it. None of those pairs may fall under AA in any theme.
        for (mode in AppThemeMode.entries) {
            for (dark in listOf(true, false)) {
                if (mode == AppThemeMode.DYNAMIC) continue // needs a real context for wallpaper colours
                val palette = MarblePalettes.resolve(mode, dark, null)
                val plate = palette.surface
                for ((name, ink) in listOf(
                    "onSurface" to palette.onSurface,
                    "onSurfaceVariant" to palette.onSurfaceVariant,
                    "primary" to palette.primary,
                )) {
                    val ratio = MarbleContrast.ratio(ink, plate)
                    assertTrue(
                        ratio >= MarbleContrast.TEXT_MINIMUM,
                        "$mode dark=$dark: $name on the card plate is only ${"%.2f".format(ratio)}:1",
                    )
                }
            }
        }
    }

    @Test
    fun aHairlineIsAlwaysAThinLineAndNeverNothing() {
        // The box border is `outlineVariant` at 55%. On a light page that composites to a calm
        // 1.27:1 line; on the AMOLED page it used to composite to 1.22:1, which is no line at all.
        // `separator()` has to keep the calm value where it already reads as an edge and lift the
        // one that would vanish.
        for ((name, outline, plate) in listOf(
            Triple("light", 0xFFC4C8D8L, 0xFFF8F8FCL),
            Triple("dark", 0xFF474956L, 0xFF15171EL),
            Triple("amoled", 0xFF67646FL, 0xFF101015L),
        )) {
            val line = MarbleContrast.separator(outline, plate, 0.55)
            val ratio = MarbleContrast.ratio(line, plate)
            // The light and dark plates already composite to a calm line (1.27 and 1.42) and
            // `separator()` leaves them alone; only the theme whose line would vanish is lifted.
            assertTrue(ratio >= 1.2, "$name: the 1 dp box hairline is only ${"%.2f".format(ratio)}:1")
            // Still a hairline, not a frame: it may not compete with body text.
            assertTrue(ratio < MarbleContrast.TEXT_MINIMUM, "$name: the hairline became a border")
        }
    }

    @Test
    fun alphaOverPaintsTheWayTheCanvasWill() {
        // 50% black over white is the mid grey, and the result is opaque.
        assertEquals(0xFF808080L, MarbleContrast.alphaOver(0xFF000000L, 0xFFFFFFFFL, 0.5))
        assertEquals(0xFFF2F1F6L, MarbleContrast.alphaOver(0xFF4355A5L, 0xFFF2F1F6L, 0.0))
        assertEquals(0xFFFFFFFFL, MarbleContrast.alphaOver(0xFF000000L, 0xFFFFFFFFL, 0.0))
    }

    @Test
    fun theAmoledHairlineIsVisibleOnItsCard() {
        // AMOLED paints near-black containers on a pure-black page. A hairline nobody can see is
        // the "boxy", structureless look the AMOLED scheme used to have. The strip is damped to
        // 55% by MarbleBox, so the *solid* outlineVariant is the upper bound: it has to clear the
        // 3:1 non-text bar, which leaves the damped line comfortably visible.
        val palette = MarblePalettes.resolve(AppThemeMode.AMOLED, dark = true, context = null)
        val ratio = MarbleContrast.ratio(palette.outlineVariant, palette.surfaceContainerLow)
        assertTrue(
            ratio >= MarbleContrast.UI_MINIMUM,
            "AMOLED outlineVariant on surfaceContainerLow is only ${"%.2f".format(ratio)}:1",
        )
        // And the container itself has to be a plate on the pure-black page, not more black.
        val plate = MarbleContrast.ratio(palette.surfaceContainerLow, 0xFF000000L)
        assertTrue(plate >= 1.05, "AMOLED boxes are invisible on the page (${"%.2f".format(plate)}:1)")
    }
}
