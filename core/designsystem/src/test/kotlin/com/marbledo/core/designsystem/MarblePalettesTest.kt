package com.marbledo.core.designsystem

import com.marbledo.domain.model.AppThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The palette table is the contract between the app theme and everything that cannot read Compose:
 * the Glance widgets and the RemoteViews notification. These tests pin the two things that used to
 * drift — *which* colour a theme resolves to, and *whether* it counts as dark.
 */
class MarblePalettesTest {

    private val namedThemes = listOf(
        AppThemeMode.AUTO,
        AppThemeMode.GLASS_LIGHT,
        AppThemeMode.DARK,
        AppThemeMode.AMOLED,
        AppThemeMode.OCEAN,
        AppThemeMode.FOREST,
        AppThemeMode.SUNSET,
        AppThemeMode.ROSE,
    )

    @Test
    fun everyThemeResolvesToTheColoursTheScreensUse() {
        for (mode in namedThemes) {
            for (dark in listOf(true, false)) {
                val scheme = marbleColorScheme(mode.name, dark, null)
                val palette = MarblePalettes.resolve(mode.name, dark, null)
                withClue("$mode dark=$dark") {
                    assertEquals(scheme.primary.toArgbLong(), palette.primary)
                    assertEquals(scheme.surface.toArgbLong(), palette.surface)
                    assertEquals(scheme.surfaceContainer.toArgbLong(), palette.surfaceContainer)
                    assertEquals(scheme.background.toArgbLong(), palette.background)
                    assertEquals(scheme.onSurfaceVariant.toArgbLong(), palette.onSurfaceVariant)
                }
            }
        }
    }

    @Test
    fun theNamedThemesAreDistinguishable() {
        val accents = namedThemes.map { MarblePalettes.resolve(it, dark = false, context = null).primary }
        assertEquals(accents.size, accents.toSet().size, "two themes share an accent: $accents")
    }

    @Test
    fun pinnedThemesIgnoreTheSystemSetting() {
        assertTrue(MarblePalettes.isDark(AppThemeMode.DARK.name, systemDark = false))
        assertTrue(MarblePalettes.isDark(AppThemeMode.AMOLED.name, systemDark = false))
        assertFalse(MarblePalettes.isDark(AppThemeMode.GLASS_LIGHT.name, systemDark = true))
        // Everything else follows the system, exactly as MarbleTheme does.
        for (mode in listOf(AppThemeMode.AUTO, AppThemeMode.OCEAN, AppThemeMode.FOREST, AppThemeMode.SUNSET, AppThemeMode.ROSE)) {
            assertEquals(true, MarblePalettes.isDark(mode.name, systemDark = true))
            assertEquals(false, MarblePalettes.isDark(mode.name, systemDark = false))
        }
    }

    @Test
    fun darkAndLightVariantsActuallyDiffer() {
        for (mode in namedThemes - AppThemeMode.AMOLED - AppThemeMode.GLASS_LIGHT) {
            withClue("$mode") {
                assertNotEquals(
                    MarblePalettes.resolve(mode.name, dark = false, null).background,
                    MarblePalettes.resolve(mode.name, dark = true, null).background,
                )
            }
        }
    }

    @Test
    fun aThemeNameNobodyRecognisesFallsBackInsteadOfThrowing() {
        val palette = MarblePalettes.resolve("NOT_A_THEME", dark = true, context = null)
        assertEquals(MarblePalettes.resolve(AppThemeMode.DARK.name, dark = true, null).primary, palette.primary)
    }

    @Test
    fun dynamicFallsBackToTheAppPaletteWithoutAContext() {
        assertNotEquals(
            0L,
            MarblePalettes.resolve(AppThemeMode.DYNAMIC, dark = false, context = null).primary,
        )
    }

    private fun withClue(clue: String, block: () -> Unit) = try {
        block()
    } catch (failure: AssertionError) {
        throw AssertionError("$clue: ${failure.message}", failure)
    }
}
