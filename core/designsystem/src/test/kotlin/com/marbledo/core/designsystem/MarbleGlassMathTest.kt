package com.marbledo.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The maths behind the liquid-glass material (docs/UI_OVERHAUL_2025.md).
 *
 * Text on glass is the risky part of the whole redesign: the tint lands on *whatever* scrolls
 * under the bar, so a label colour that reads over the page can still fail over a white card or a
 * coloured container. These tests pin the guarantee — for every theme in the app and every
 * surface the theme owns, the computed inks clear their WCAG bars over the composited glass — and
 * the pure indicator geometry the morphing pill is drawn from.
 */
class MarbleGlassMathTest {

    private val modes = listOf("AUTO", "OCEAN", "FOREST", "SUNSET", "ROSE", "DARK", "AMOLED", "GLASS_LIGHT")

    private fun samplesOf(palette: MarblePalette): List<Long> = listOf(
        palette.background,
        palette.surface,
        palette.surfaceContainerLow,
        palette.surfaceContainer,
        palette.surfaceContainerHigh,
        palette.primaryContainer,
    )

    /** The same dark test the material uses in composition: derived from the scheme's luminance. */
    private fun isDark(palette: MarblePalette): Boolean =
        MarbleContrast.bestInk(palette.background) == MarbleContrast.INK_LIGHT

    @Test
    fun labelAndIconInkOnGlassReadOverEveryThemeAndEveryBackdrop() {
        for (mode in modes) {
            for (dark in listOf(false, true)) {
                val palette = MarblePalettes.resolve(mode, dark, null)
                val recipe = MarbleGlass.recipe(palette.surface, palette.outlineVariant, isDark(palette))
                val backdrops = samplesOf(palette)
                val label = MarbleGlass.inkOnGlass(
                    preferred = palette.onSurfaceVariant,
                    recipe = recipe,
                    backdrops = backdrops,
                    minimum = MarbleContrast.TEXT_MINIMUM,
                )
                val icon = MarbleGlass.inkOnGlass(
                    preferred = palette.onSurfaceVariant,
                    recipe = recipe,
                    backdrops = backdrops,
                    minimum = MarbleContrast.UI_MINIMUM,
                )
                for (backdrop in backdrops) {
                    val plate = MarbleGlass.composite(recipe, backdrop)
                    assertTrue(
                        MarbleContrast.ratio(label, plate) >= MarbleContrast.TEXT_MINIMUM,
                        "$mode/dark=$dark: label ${label.toString(16)} fails on plate ${plate.toString(16)}",
                    )
                    assertTrue(
                        MarbleContrast.ratio(icon, plate) >= MarbleContrast.UI_MINIMUM,
                        "$mode/dark=$dark: icon ${icon.toString(16)} fails on plate ${plate.toString(16)}",
                    )
                }
            }
        }
    }

    @Test
    fun theHairlineSurvivesTheAmoledPlate() {
        val palette = MarblePalettes.resolve("AMOLED", true, null)
        val recipe = MarbleGlass.recipe(palette.surface, palette.outlineVariant, isDark = true)
        val plate = MarbleGlass.composite(recipe, palette.surface)
        assertTrue(
            MarbleContrast.ratio(recipe.hairlineArgb, plate) >= 1.8,
            "hairline vanished on the AMOLED glass plate",
        )
    }

    @Test
    fun theRecipeKeepsTheTintWithinTheGlassRange() {
        for (mode in modes) {
            val palette = MarblePalettes.resolve(mode, dark = false, context = null)
            val recipe = MarbleGlass.recipe(palette.surface, palette.outlineVariant, isDark(palette))
            assertTrue(recipe.tintAlpha in 0.5..0.9, "$mode: tint alpha ${recipe.tintAlpha}")
            assertTrue(recipe.noiseAlpha in 0.0..0.1, "$mode: noise alpha ${recipe.noiseAlpha}")
            assertTrue(recipe.sheenAlpha in 0.0..1.0, "$mode: sheen alpha ${recipe.sheenAlpha}")
        }
    }

    @Test
    fun indicatorCentersMirrorInRtl() {
        assertEquals(0.125f, MarbleGlass.indicatorCenterFraction(0, 4, rtl = false))
        assertEquals(0.375f, MarbleGlass.indicatorCenterFraction(1, 4, rtl = false))
        assertEquals(0.875f, MarbleGlass.indicatorCenterFraction(0, 4, rtl = true))
        assertEquals(0.625f, MarbleGlass.indicatorCenterFraction(1, 4, rtl = true))
        assertEquals(0.5f, MarbleGlass.indicatorCenterFraction(0, 1, rtl = false))
        // Out-of-range indices are clamped, never crash.
        assertEquals(0.875f, MarbleGlass.indicatorCenterFraction(9, 4, rtl = false))
        assertEquals(0.125f, MarbleGlass.indicatorCenterFraction(-3, 4, rtl = false))
    }

    @Test
    fun theIndicatorSqueezesMidTravelAndSettlesAtRest() {
        val base = MarbleGlass.INDICATOR_WIDTH_BASE
        val squeezed = base - MarbleGlass.INDICATOR_SQUEEZE
        // Exactly at a slot centre the pill is at rest width.
        assertEquals(base, MarbleGlass.indicatorWidthFraction(0.125f, 4), 0.0001f)
        assertEquals(base, MarbleGlass.indicatorWidthFraction(0.625f, 4), 0.0001f)
        // Halfway between two slots it is squeezed — the morph.
        assertEquals(squeezed, MarbleGlass.indicatorWidthFraction(0.25f, 4), 0.0001f)
        assertEquals(squeezed, MarbleGlass.indicatorWidthFraction(0.5f, 4), 0.0001f)
        // And the width never leaves the (0, 1] range anywhere in between.
        for (step in 0..100) {
            val fraction = MarbleGlass.indicatorWidthFraction(step / 100f, 4)
            assertTrue(fraction > 0f && fraction <= 1f, "width fraction out of range: $fraction")
        }
    }

    @Test
    fun grainIsDeterministicBoundedAndOpaque() {
        val first = MarbleGlass.grainPixels(MarbleGlass.GRAIN_SEED, 64)
        val second = MarbleGlass.grainPixels(MarbleGlass.GRAIN_SEED, 64)
        assertArrayEquals(first, second)
        for (pixel in first) {
            val alpha = (pixel ushr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            assertEquals(0xFF, alpha)
            assertEquals(red, green)
            assertEquals(green, blue)
        }
    }

    @Test
    fun clearanceSumsTheWholeBarZone() {
        // 24 dp system bars + 12 dp float gap + 62 dp bar + 10 dp breathing room.
        assertEquals(108.dp, MarbleGlass.clearance(24.dp))
    }

    @Test
    fun readableAcrossRaisesTheWorstCasePair() {
        // Pale ink that fails on both a white card and a soft grey page.
        val pale = 0xFFBBC5FFL
        val backgrounds = listOf(0xFFFFFFFFL, 0xFFE4E7F1L)
        val fixed = MarbleContrast.readableAcross(pale, backgrounds, MarbleContrast.TEXT_MINIMUM)
        for (background in backgrounds) {
            assertTrue(
                MarbleContrast.ratio(fixed, background) >= MarbleContrast.TEXT_MINIMUM,
                "readableAcross left ${fixed.toString(16)} failing on ${background.toString(16)}",
            )
        }
    }
}
