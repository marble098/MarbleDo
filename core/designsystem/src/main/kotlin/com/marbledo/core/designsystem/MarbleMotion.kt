@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbledo.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's single motion vocabulary, taken from the Material 3 Expressive motion scheme.
 *
 * Material's guidance is that *every* moving part of a product is timed by the theme's motion
 * scheme — the same spring for the same kind of change — so a chip, a card and a list row feel like
 * one product instead of three. [`MarbleTheme`] already installs `MotionScheme.expressive()` (or
 * `standard()` when the user asks for less movement) and every stock Material component picks it up
 * on its own; these helpers are for the app's *own* animations, which used to be hand-written
 * `tween(...)` values that drifted away from the components around them.
 *
 * Two sizes of change, following Material's vocabulary:
 *
 *  * **effects** — colour, alpha, elevation: fast, non-directional.
 *  * **spatial** — position, size, progress: springy, because something physically moved.
 *
 * When reduced motion is on, every spec collapses to [snap] so custom animations stop too, exactly
 * like the components that already honour the setting.
 */
object MarbleMotion {
    /** Colour, alpha and elevation changes. */
    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

    /** The quicker effects spring, for small toggles inside a row. */
    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

    /** Position, size and progress changes. */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()
}
