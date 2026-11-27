package com.marbledo.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * The finished recipe of one liquid-glass surface: the tint that adapts to the theme, the
 * specular sheen on the top edge, the film grain that keeps large blur fields from banding, and
 * the hairline that gives the glass an edge at all.
 *
 * The recipe is derived, never hand-painted: [MarbleGlass.recipe] computes the hairline through
 * [MarbleContrast.separator] so it survives the AMOLED plate, and [MarbleGlass.inkOnGlass]
 * computes the label ink against every sample the material can land on so text on glass always
 * clears its contrast bar (docs/UI_OVERHAUL_2025.md).
 */
data class GlassRecipe(
    /** Opaque colour of the tint layer; drawn at [tintAlpha]. */
    val tintArgb: Long,
    /** How much of the tint shows; the rest is the blurred backdrop. */
    val tintAlpha: Double,
    /** Strength of the top-lit specular highlight. */
    val sheenAlpha: Double,
    /** Strength of the film grain. */
    val noiseAlpha: Double,
    /** Hairline colour, already lifted so the composited line is visible on its own plate. */
    val hairlineArgb: Long,
)

/**
 * The liquid-glass design tokens and the pure geometry of the floating glass tab bar.
 *
 * Every dimension of the glass material lives here; screens and components take them from this
 * object instead of inventing literals (docs/UI_OVERHAUL_2025.md, "Design tokens"). The maths is
 * plain Kotlin over `0xAARRGGBB` longs — the same convention as [MarbleContrast] and
 * [MarblePalette] — so it runs and is tested in a plain JVM unit test.
 */
object MarbleGlass {

    // ── Geometry ────────────────────────────────────────────────────────────────────────────

    /** Backdrop blur radius of the glass. Below Android 12 the blur degrades to tint + grain. */
    val blurRadius: Dp = 24.dp

    /** Horizontal margin of the floating bar — the glass never touches the screen edges. */
    val floatMargin: Dp = 18.dp

    /** Gap between the floating bar and the bottom edge / system navigation area. */
    val floatGap: Dp = 12.dp

    /** Breathing room kept between the top of the floating bar and screen content. */
    val clearanceGap: Dp = 10.dp

    /** Height of the floating glass tab bar. */
    val barHeight: Dp = 62.dp

    /** Height of the morphing selection pill; it embraces the icon *and* its label. */
    val indicatorHeight: Dp = 48.dp

    /** Corner radius of large glass panels (the navigation rail). */
    val corner: Dp = 28.dp

    /** Hairline drawn at 2 dp so the inner half reads as a 1 dp line after the shape clips it. */
    val hairlineWidth: Dp = 2.dp

    /** Icon size inside a tab cell. */
    val iconSize: Dp = 24.dp

    /** Accumulated scroll distance after which the bar follows the scroll direction. */
    val scrollThreshold: Dp = 40.dp

    /** Rest width of the selection pill as a fraction of one slot. */
    const val INDICATOR_WIDTH_BASE = 0.62f

    /** How much the pill squeezes while it travels between slots (the "morph"). */
    const val INDICATOR_SQUEEZE = 0.12f

    /** Scale of the selected icon; the micro-interaction of the tab morph. */
    const val SELECTED_ICON_SCALE = 1.12f

    // ── Material recipe ─────────────────────────────────────────────────────────────────────

    private val LIGHT_TINT = 0xFFF6F7FEL
    private val DARK_TINT = 0xFF1B1E2AL
    private const val LIGHT_TINT_ALPHA = 0.72
    private const val DARK_TINT_ALPHA = 0.66
    private const val LIGHT_SHEEN_ALPHA = 0.55
    private const val DARK_SHEEN_ALPHA = 0.14
    private const val LIGHT_NOISE_ALPHA = 0.035
    private const val DARK_NOISE_ALPHA = 0.055
    private const val HAIRLINE_ALPHA = 0.55

    /** Seed of the film grain; fixed so the texture is identical on every frame and device. */
    const val GRAIN_SEED = 0x5EED

    /** Edge length of the square grain tile. */
    const val GRAIN_SIZE = 96

    /**
     * The glass recipe of the current theme.
     *
     * [surface] and [outlineVariant] come from the resolved colour scheme, so one theme switch
     * re-tints the glass exactly like every other surface. The hairline is computed with
     * [MarbleContrast.separator] against the tinted plate — the AMOLED scheme is where a
     * hand-painted hairline used to vanish.
     */
    fun recipe(surface: Long, outlineVariant: Long, isDark: Boolean): GlassRecipe {
        val tint = if (isDark) DARK_TINT else LIGHT_TINT
        val tintAlpha = if (isDark) DARK_TINT_ALPHA else LIGHT_TINT_ALPHA
        val plate = MarbleContrast.alphaOver(tint, surface, tintAlpha)
        return GlassRecipe(
            tintArgb = tint,
            tintAlpha = tintAlpha,
            sheenAlpha = if (isDark) DARK_SHEEN_ALPHA else LIGHT_SHEEN_ALPHA,
            noiseAlpha = if (isDark) DARK_NOISE_ALPHA else LIGHT_NOISE_ALPHA,
            hairlineArgb = MarbleContrast.separator(outlineVariant, plate, HAIRLINE_ALPHA),
        )
    }

    /** The colour the tinted glass lands on over an opaque [backdrop]. */
    fun composite(recipe: GlassRecipe, backdrop: Long): Long =
        MarbleContrast.alphaOver(recipe.tintArgb, backdrop, recipe.tintAlpha)

    /**
     * Ink that reads on the glass no matter what scrolls under it: checked against the composite
     * of every sample backdrop at once, not only the page colour.
     */
    fun inkOnGlass(preferred: Long, recipe: GlassRecipe, backdrops: List<Long>, minimum: Double): Long =
        MarbleContrast.readableAcross(preferred, backdrops.map { composite(recipe, it) }, minimum)

    /** How much screen content must keep clear of the floating bar above the system bars. */
    fun clearance(navBarsBottom: Dp): Dp = navBarsBottom + floatGap + barHeight + clearanceGap

    // ── Indicator geometry (pure, mirrored for RTL) ─────────────────────────────────────────

    /** Horizontal centre of slot [index] as a fraction of the bar width; mirrored in RTL. */
    fun indicatorCenterFraction(index: Int, count: Int, rtl: Boolean): Float {
        if (count <= 1) return 0.5f
        val i = index.coerceIn(0, count - 1)
        val center = (i + 0.5f) / count
        return if (rtl) 1f - center else center
    }

    /**
     * Width of the travelling pill as a fraction of one slot: full at rest, squeezed at the
     * midpoint between two slots. The squeeze is the morph — the pill behaves like a drop of
     * liquid pulled between two anchors instead of a box that teleports.
     */
    fun indicatorWidthFraction(centerFraction: Float, count: Int): Float {
        if (count <= 1) return INDICATOR_WIDTH_BASE
        val slot = 1f / count
        val slotUnits = centerFraction / slot - 0.5f
        val distance = abs(slotUnits - floor(slotUnits + 0.5f))
        val midTravel = (distance / 0.5f).coerceIn(0f, 1f)
        return (INDICATOR_WIDTH_BASE - INDICATOR_SQUEEZE * midTravel).coerceIn(0.05f, 1f)
    }

    // ── Film grain ──────────────────────────────────────────────────────────────────────────

    /**
     * Deterministic grey grain as `0xAARRGGBB` pixels; the Android layer wraps these into the
     * tiled noise brush. Deterministic so the texture never shimmers between frames.
     */
    fun grainPixels(seed: Int, count: Int): IntArray {
        val random = kotlin.random.Random(seed)
        return IntArray(count) {
            val shade = random.nextInt(256)
            (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
    }
}
