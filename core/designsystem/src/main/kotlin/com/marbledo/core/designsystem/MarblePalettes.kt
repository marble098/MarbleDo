package com.marbledo.core.designsystem

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.marbledo.domain.model.AppThemeMode

/**
 * The app theme as plain ARGB values, for the surfaces Compose cannot reach.
 *
 * The dashboard paints itself from [MarbleTheme], but the Glance widgets and the RemoteViews
 * notification are built outside the Compose tree. Before this existed each of them carried its own
 * hand-written colour table, so choosing Ocean, Forest, Sunset or Rose repainted the screens and left
 * the home-screen widget and the ongoing notification on the old indigo. [resolve] now reads the very
 * same [ColorScheme] the app renders, which is why one theme switch repaints all three.
 *
 * Values are `0xAARRGGBB` longs (the same order as `android.graphics.Color`), so a widget can use
 * them through [Color] and a notification can hand them to the framework unchanged.
 */
data class MarblePalette(
    val isDark: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val onBackground: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val outline: Long,
    val outlineVariant: Long,
) {
    /** The accent as a Compose colour, for Glance's `ColorProvider`. */
    val accent: Color get() = Color(primary)

    /** The card fill as a Compose colour, for Glance's `ColorProvider`. */
    val cardColor: Color get() = Color(surface)
}

object MarblePalettes {

    /**
     * Whether a theme renders dark right now. Named themes pin themselves; the rest follow the
     * system setting exactly as [MarbleTheme] does.
     */
    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        "DARK", "AMOLED" -> true
        "GLASS_LIGHT" -> false
        else -> systemDark
    }

    /** The palette of the app theme the user picked, in the current light or dark mode. */
    fun resolve(mode: AppThemeMode, dark: Boolean, context: Context? = null): MarblePalette =
        resolve(mode.name, dark, context)

    /**
     * The palette of a theme name. [context] is only read for [AppThemeMode.DYNAMIC], where the
     * system owns the colours and they can only be read from a real context; without one the app's
     * own indigo palette is used, which is also what the app falls back to when dynamic colour is
     * unavailable. Never throws — a widget update must not fail because of a theme name.
     */
    fun resolve(mode: String, dark: Boolean, context: Context? = null): MarblePalette =
        runCatching { marbleColorScheme(mode, dark, context) }.getOrElse { marbleColorScheme("AUTO", dark, null) }
            .toMarblePalette(dark)
}

/** Reads the palette out of a colour scheme so there is exactly one colour table in the app. */
fun ColorScheme.toMarblePalette(isDark: Boolean): MarblePalette = MarblePalette(
    isDark = isDark,
    background = background.toArgbLong(),
    surface = surface.toArgbLong(),
    surfaceContainer = surfaceContainer.toArgbLong(),
    surfaceContainerHigh = surfaceContainerHigh.toArgbLong(),
    onBackground = onBackground.toArgbLong(),
    onSurfaceVariant = onSurfaceVariant.toArgbLong(),
    primary = primary.toArgbLong(),
    onPrimary = onPrimary.toArgbLong(),
    primaryContainer = primaryContainer.toArgbLong(),
    onPrimaryContainer = onPrimaryContainer.toArgbLong(),
    outline = outline.toArgbLong(),
    outlineVariant = outlineVariant.toArgbLong(),
)

/**
 * The colour as `0xAARRGGBB`.
 *
 * Computed from the channels rather than through `Color.toArgb()` so the table can be read in a
 * plain JVM unit test, without an Android graphics stack behind it.
 */
internal fun Color.toArgbLong(): Long {
    fun channel(value: Float): Long = (value.coerceIn(0f, 1f) * 255f + 0.5f).toLong().coerceIn(0L, 255L)
    return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}
