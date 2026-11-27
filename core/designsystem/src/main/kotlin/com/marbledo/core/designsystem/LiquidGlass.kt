package com.marbledo.core.designsystem

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * How much screen content must keep clear of the floating glass tab bar (plus the system
 * navigation area underneath it). The app shell provides the real value; scrollables add it to
 * their bottom content padding and bottom-aligned controls sit above it. Defaults to 0 dp so a
 * surface used outside the shell (previews, sheets) reserves nothing.
 */
val LocalGlassNavInset = staticCompositionLocalOf { 0.dp }

/**
 * The glass recipe of the active theme, derived from the resolved colour scheme — the same table
 * the rest of the app paints from, so one theme switch re-tints the glass too.
 */
@Composable
fun glassRecipe(): GlassRecipe {
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background.toArgbLong()
    val isDark = MarbleContrast.bestInk(background) == MarbleContrast.INK_LIGHT
    return remember(background, scheme.surface, scheme.outlineVariant) {
        MarbleGlass.recipe(
            surface = scheme.surface.toArgbLong(),
            outlineVariant = scheme.outlineVariant.toArgbLong(),
            isDark = isDark,
        )
    }
}

/**
 * The colour a glass plate lands on when nothing but the page background is behind it — used for
 * glass surfaces in a flat position (the navigation rail) that never show scrolling content.
 */
@Composable
fun glassTintOnBackground(): Color {
    val recipe = glassRecipe()
    val background = MaterialTheme.colorScheme.background.toArgbLong()
    return remember(recipe, background) { Color(MarbleGlass.composite(recipe, background)) }
}

/**
 * Records everything drawn inside this element into [layer] every frame, so liquid-glass
 * surfaces floating above can blur what actually scrolls underneath them (the "backdrop" half of
 * liquid glass). Capture is a display-list record, not a raster, and only runs on Android 12+
 * where the blur exists; older platforms render the tint + grain fallback instead.
 */
fun Modifier.glassBackdropSource(layer: GraphicsLayer): Modifier =
    drawWithContent {
        if (backdropBlurSupported()) {
            layer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            ) {
                this@drawWithContent.drawContent()
            }
        }
        drawContent()
    }

private fun backdropBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The tiled film grain: one tiny deterministic bitmap sampled as a repeating shader, drawn at a
 * whisper of alpha so the blur field never bands into visible steps.
 */
@Composable
private fun rememberNoiseBrush(): ShaderBrush {
    return remember {
        val side = MarbleGlass.GRAIN_SIZE
        val pixels = MarbleGlass.grainPixels(MarbleGlass.GRAIN_SEED, side * side)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * The liquid-glass material itself (docs/UI_OVERHAUL_2025.md, "Deep material"):
 *
 *  1. a real blurred capture of the content behind the surface (Android 12+),
 *  2. the adaptive tint that lets the theme through,
 *  3. film grain against banding,
 *  4. a top-lit specular highlight,
 *  5. a hairline that keeps the glass an edge on every theme.
 *
 * [backdropOrigin] is the position-in-window of the recorded content; the surface measures its
 * own position and shifts the capture so the blur samples exactly what is behind it. Pass
 * `backdrop = null` for glass on a flat background (the blur would capture nothing anyway).
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MarbleGlass.barHeight / 2),
    recipe: GlassRecipe = glassRecipe(),
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val noiseBrush = rememberNoiseBrush()
    val blurPx = with(LocalDensity.current) { MarbleGlass.blurRadius.toPx() }
    var ownOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { ownOrigin = it.positionInWindow() }
            .clip(shape),
    ) {
        if (backdrop != null && backdropBlurSupported()) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal) }
                    .drawWithContent {
                        translate(backdropOrigin.x - ownOrigin.x, backdropOrigin.y - ownOrigin.y) {
                            drawLayer(backdrop)
                        }
                    },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawGlassMaterial(recipe, noiseBrush) },
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Tint, grain, sheen and hairline — everything except the blurred backdrop. */
private fun DrawScope.drawGlassMaterial(recipe: GlassRecipe, noiseBrush: ShaderBrush) {
    drawRect(Color(recipe.tintArgb), alpha = recipe.tintAlpha.toFloat())
    drawRect(noiseBrush, alpha = recipe.noiseAlpha.toFloat())
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = recipe.sheenAlpha.toFloat()),
            0.55f to Color.Transparent,
        ),
    )
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(recipe.hairlineArgb),
            0.45f to Color(recipe.hairlineArgb).copy(alpha = 0.35f),
            1f to Color(recipe.hairlineArgb).copy(alpha = 0.55f),
        ),
        style = Stroke(width = MarbleGlass.hairlineWidth.toPx()),
    )
}
