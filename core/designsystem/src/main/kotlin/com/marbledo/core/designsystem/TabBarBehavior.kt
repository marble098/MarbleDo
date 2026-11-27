package com.marbledo.core.designsystem

/**
 * The auto-hide state machine of the floating glass tab bar (docs/UI_OVERHAUL_2025.md,
 * "Smart behaviour").
 *
 * The bar watches nested-scroll deltas and follows the reading direction: scrolling toward the
 * end of a list slides the bar away so content owns the screen; any deliberate scroll back brings
 * it home. A small wobble never moves the bar — the direction has to be *sustained* for
 * [thresholdPx] pixels. Pure Kotlin on purpose: the whole behaviour is unit-tested without a
 * device ([TabBarBehaviorTest]).
 */
data class TabBarAutoHide(
    val visible: Boolean = true,
    /** Signed accumulator: negative while scrolling toward the end, positive toward the start. */
    val pendingPx: Float = 0f,
) {
    /**
     * One nested-scroll sample. [deltaY] follows Compose's convention: negative when the content
     * scrolls toward its end (finger moves up). Crossing [thresholdPx] commits the new state.
     */
    fun scrolled(deltaY: Float, thresholdPx: Float): TabBarAutoHide {
        if (deltaY == 0f || thresholdPx <= 0f) return this
        val towardEnd = deltaY < 0f
        val continuing = pendingPx != 0f && (pendingPx < 0f) == towardEnd
        val accumulated = (if (continuing) pendingPx else 0f) + deltaY
        return when {
            accumulated <= -thresholdPx -> TabBarAutoHide(visible = false, pendingPx = 0f)
            accumulated >= thresholdPx -> TabBarAutoHide(visible = true, pendingPx = 0f)
            else -> copy(pendingPx = accumulated)
        }
    }

    /** Back to visible with a clean accumulator — used when the user switches tabs. */
    fun reset(): TabBarAutoHide = TabBarAutoHide()
}
