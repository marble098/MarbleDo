package com.marbledo.core.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * WCAG 2.1 contrast maths for every surface that is painted outside the Compose tree.
 *
 * The screens inherit their text colours from the Material colour scheme, which guarantees the
 * pairs are readable. The notification card and the date badge do not: they pick a colour from one
 * palette (the app theme) and paint it on a background that belongs to someone else (SystemUI's
 * shade, or a plate the artwork itself drew). A pale accent on a white card, or white digits on a
 * pale plate, is exactly how the shade ended up with unreadable parts in dark themes.
 *
 * These helpers keep the hue and only move the lightness, so a themed accent stays recognisably
 * that theme while meeting the 4.5:1 text bar (WCAG AA) or the 3:1 bar for large text and UI
 * shapes (WCAG AA for non-text contrast).
 *
 * Values are `0xAARRGGBB` longs, the same convention as [MarblePalette], so the functions also run
 * in a plain JVM unit test with no Android graphics stack behind them.
 */
object MarbleContrast {
    /** WCAG AA for body text. */
    const val TEXT_MINIMUM = 4.5

    /** WCAG AA for large text, icons and UI shapes. */
    const val UI_MINIMUM = 3.0

    /** The darkest ink the app uses instead of pure black, for a softer but still readable text. */
    const val INK_DARK = 0xFF101014L

    /** The light ink used on filled surfaces. */
    const val INK_LIGHT = 0xFFFFFFFFL

    /** WCAG relative luminance of an `0xAARRGGBB` colour, ignoring alpha. */
    fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Contrast ratio of two opaque colours: 1.0 (identical) to 21.0 (black on white). */
    fun ratio(foreground: Long, background: Long): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    fun ratio(foreground: Int, background: Int): Double = ratio(foreground.toArgb(), background.toArgb())

    /** [colour] mixed [fraction] of the way towards [towards]; alpha is returned fully opaque. */
    fun blend(colour: Long, towards: Long, fraction: Double): Long {
        val f = fraction.coerceIn(0.0, 1.0)
        fun mix(shift: Int): Long {
            val from = (colour shr shift) and 0xFF
            val to = (towards shr shift) and 0xFF
            return (from + (to - from) * f).roundToLong().coerceIn(0L, 255L)
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * [foreground] kept as is when it already reads on [background], otherwise stepped towards
     * black or white — whichever direction raises the contrast — until it reaches [minimum].
     */
    fun readable(foreground: Long, background: Long, minimum: Double = TEXT_MINIMUM): Long {
        if (ratio(foreground, background) >= minimum) return foreground
        // Whichever pole is farther from the background is the one that can actually reach the bar.
        val target = if (ratio(INK_DARK, background) > ratio(INK_LIGHT, background)) INK_DARK else INK_LIGHT
        var fraction = 0.0
        while (fraction < 1.0) {
            fraction = (fraction + 0.05).coerceAtMost(1.0)
            val candidate = blend(foreground, target, fraction)
            if (ratio(candidate, background) >= minimum) return candidate
        }
        return target
    }

    fun readable(foreground: Int, background: Int, minimum: Double = TEXT_MINIMUM): Int =
        readable(foreground.toArgb(), background.toArgb(), minimum).toArgbInt()

    /** The ink that reads best on a filled [background]: near-black on light plates, white on dark. */
    fun bestInk(background: Long): Long =
        if (ratio(INK_DARK, background) >= ratio(INK_LIGHT, background)) INK_DARK else INK_LIGHT

    fun bestInk(background: Int): Int = bestInk(background.toArgb()).toArgbInt()

    /**
     * A 1 dp separator painted at [alpha] over [background], lifted until the *composited* line
     * clears [minimum].
     *
     * The app draws every block as a hairline-bordered card. Those hairlines are deliberately
     * quiet, but a quiet line still has to exist: on the AMOLED scheme an `outlineVariant` at 55%
     * over a near-black plate composited to a 1.2:1 smudge, so the cards read as loose text with no
     * structure at all — the "boxy but invisible" look. Lifting the *result* rather than raising
     * the alpha keeps light themes exactly as calm as they were (they already pass) and only moves
     * the themes where the line had disappeared.
     */
    fun separator(
        colour: Long,
        background: Long,
        alpha: Double,
        minimum: Double = 1.8,
    ): Long {
        val composite = alphaOver(colour, background, alpha)
        if (ratio(composite, background) >= minimum) return composite
        return readable(composite, background, minimum)
    }

    /** [foreground] at [alpha] painted over an opaque [background], returned opaque. */
    fun alphaOver(foreground: Long, background: Long, alpha: Double): Long {
        val a = alpha.coerceIn(0.0, 1.0)
        fun mix(shift: Int): Long {
            val from = (foreground shr shift) and 0xFF
            val to = (background shr shift) and 0xFF
            return (from * a + to * (1 - a)).roundToLong().coerceIn(0L, 255L)
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** [colour] with the given alpha, for the tinted containers inside the card. */
    fun withAlpha(colour: Long, alpha: Double): Long {
        val value = (alpha.coerceIn(0.0, 1.0) * 255).roundToLong().coerceIn(0L, 255L)
        return (value shl 24) or (colour and 0x00FFFFFFL)
    }
}

/**
 * Ints and longs carry the same `0xAARRGGBB` bits. Widening through `and 0xFFFFFFFFL` keeps the
 * alpha byte intact for the maths above and truncating back restores the exact Int.
 */
internal fun Int.toArgb(): Long = toLong() and 0xFFFFFFFFL

internal fun Long.toArgbInt(): Int = (this and 0xFFFFFFFFL).toInt()
