package com.marbledo.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The auto-hide state machine of the floating glass tab bar.
 *
 * The rule is "sustained direction, not jitter": scrolling toward the end of a list hides the bar
 * after 40 dp of travel, any deliberate scroll back brings it home, and a one-pixel wobble moves
 * nothing. These tests are the reason the behaviour can live in a plain data class.
 */
class TabBarBehaviorTest {

    private val threshold = 40f

    @Test
    fun startsVisibleWithACleanAccumulator() {
        val state = TabBarAutoHide()
        assertTrue(state.visible)
        assertEquals(0f, state.pendingPx)
    }

    @Test
    fun hidesAfterASustainedDownwardFlick() {
        var state = TabBarAutoHide()
        state = state.scrolled(-30f, threshold)
        assertTrue(state.visible, "below the threshold nothing moves")
        state = state.scrolled(-15f, threshold)
        assertFalse(state.visible, "the sustained flick commits the hide")
        assertEquals(0f, state.pendingPx, "the accumulator drains on commit")
    }

    @Test
    fun showsAgainWhenScrolledBackUp() {
        var state = TabBarAutoHide(visible = false)
        state = state.scrolled(25f, threshold)
        assertFalse(state.visible)
        state = state.scrolled(20f, threshold)
        assertTrue(state.visible, "a sustained scroll back reveals the bar")
    }

    @Test
    fun staysHiddenWhileScrollingTowardTheEnd() {
        var state = TabBarAutoHide(visible = false)
        repeat(5) { state = state.scrolled(-threshold, threshold) }
        assertFalse(state.visible)
        assertEquals(0f, state.pendingPx)
    }

    @Test
    fun aDirectionFlipRestartsTheAccumulator() {
        var state = TabBarAutoHide()
        state = state.scrolled(-30f, threshold)
        // The user reverses before committing: the negative travel must not carry over.
        state = state.scrolled(35f, threshold)
        assertTrue(state.visible, "the reversed travel hides nothing and reveals nothing new")
        // And the old negative travel cannot team up with a fresh flick afterwards.
        state = state.scrolled(-20f, threshold)
        assertTrue(state.visible, "the stale negative travel was discarded on the flip")
    }

    @Test
    fun aSmallWobbleNeverMovesTheBar() {
        var state = TabBarAutoHide()
        repeat(20) {
            state = state.scrolled(3f, threshold)
            state = state.scrolled(-3f, threshold)
        }
        assertTrue(state.visible, "jitter must never hide or move the bar")
        assertTrue(kotlin.math.abs(state.pendingPx) < threshold, "the accumulator never commits")
    }

    @Test
    fun zeroAndZeroThresholdAreIgnored() {
        val state = TabBarAutoHide()
        assertEquals(state, state.scrolled(0f, threshold))
        assertEquals(state, state.scrolled(-100f, 0f))
    }

    @Test
    fun resetAlwaysReturnsHome() {
        val state = TabBarAutoHide(visible = false, pendingPx = -33f).reset()
        assertTrue(state.visible)
        assertEquals(0f, state.pendingPx)
    }
}
