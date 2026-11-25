package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test

class MultiFingerStateTest {
    private fun pair(x: Float = 100f, y: Float = 100f, span: Float = 100f) = listOf(
        MultiFingerState.Point(7, x - span / 2, y), MultiFingerState.Point(19, x + span / 2, y))
    private fun state(allowTap: Boolean = true) = MultiFingerState(8f).apply { begin(pair(), 0L, allowTap) }
    private fun assertEmpty(output: MultiFingerState.Output) = assertEquals(MultiFingerState.Output(), output)

    @Test fun tapIsEmittedOnlyOnceAfterFinalRelease() {
        val s = state()
        s.move(pair())
        s.contactsChanged(pair().take(1))
        assertTrue(s.finish(120L))
        assertFalse(s.finish(121L))
    }

    @Test fun tapDoesNotDependOnFingerSpacing() {
        val s = MultiFingerState(8f)
        s.begin(pair(span = 12f), 0L, true)
        s.contactsChanged(pair(span = 12f).takeLast(1))
        assertTrue(s.finish(120L))
    }

    @Test fun holdAndPriorMouseMovementAreNotRightClicks() {
        assertFalse(state().finish(700L))
        assertFalse(state(allowTap = false).finish(100L))
    }

    @Test fun parallelMotionScrollsBothAxesWithoutZoom() {
        val s = state()
        assertEquals(MultiFingerState.Output(12f, 20f), s.move(pair(112f, 120f)))
        assertEquals(MultiFingerState.Output(3f, -5f), s.move(pair(115f, 115f)))
        s.contactsChanged(pair(115f, 115f).take(1))
        assertFalse(s.finish(150L))
    }

    @Test fun jitterBelowThresholdProducesNoScrollOrZoom() {
        val s = state()
        assertEmpty(s.move(pair(102f, 103f, 103f)))
        assertEmpty(s.move(pair(99f, 101f, 98f)))
        assertTrue(s.finish(120L))
    }

    @Test fun spreadingAndPinchingEmitOppositeZoomDirections() {
        val out = state().move(pair(span = 125f))
        val inward = state().move(pair(span = 75f))
        assertTrue(out.zoom > 0)
        assertTrue(inward.zoom < 0)
        assertEquals(0f, out.dx, 0f)
        assertEquals(0f, inward.dy, 0f)
    }

    @Test fun slowPinchAccumulatesSmallScaleChanges() {
        val s = state()
        var zoom = s.move(pair(span = 120f)).zoom
        for (span in 121..150) zoom += s.move(pair(span = span.toFloat())).zoom
        assertEquals(4, zoom)
    }

    @Test fun scaleReversalDoesNotStickOrEmitScroll() {
        val s = state()
        val forward = s.move(pair(span = 125f))
        val backward = s.move(pair(span = 100f))
        assertTrue(forward.zoom > 0 && backward.zoom < 0)
        assertEquals(0, forward.zoom + backward.zoom)
        assertFalse(s.finish(120L))
    }

    @Test fun scrollCannotTurnIntoPinchMidGesture() {
        val s = state()
        s.move(pair(y = 120f))
        assertEmpty(s.move(pair(y = 120f, span = 150f)))
        assertEquals(MultiFingerState.Mode.SCROLL, s.mode)
    }

    @Test fun pinchCannotTurnIntoScrollMidGesture() {
        val s = state()
        s.move(pair(span = 125f))
        assertEmpty(s.move(pair(x = 180f, y = 180f, span = 125f)))
        assertEquals(MultiFingerState.Mode.PINCH, s.mode)
    }

    @Test fun pointerIndexReorderingDoesNotJump() {
        val s = state()
        assertEmpty(s.move(pair().reversed()))
        assertEquals(MultiFingerState.Output(0f, 20f), s.move(pair(y = 120f).reversed()))
    }

    @Test fun pointerReplacementCannotReuseOldCoordinates() {
        val s = state()
        val points = pair().map { it.copy(id = it.id + 100) }
        assertEmpty(s.move(points))
        assertEquals(MultiFingerState.Mode.BLOCKED, s.mode)
        assertFalse(s.finish(120L))
    }

    @Test fun thirdAndFourthFingerBlockRestOfChain() {
        val s = state()
        s.contactsChanged(pair() + MultiFingerState.Point(30, 0f, 0f))
        s.contactsChanged(pair())
        assertEmpty(s.move(pair(y = 200f)))
        s.contactsChanged(pair().take(1))
        assertFalse(s.finish(150L))
    }

    @Test fun startingWithThreeFingersCannotScrollOrClick() {
        val s = MultiFingerState(8f)
        s.begin(pair() + MultiFingerState.Point(30, 0f, 0f), 0L, true)
        assertEmpty(s.move(pair(y = 200f)))
        assertFalse(s.finish(150L))
    }

    @Test fun remainingFingerCannotMoveMouseOrResumeScroll() {
        val s = state()
        s.move(pair(y = 120f))
        s.contactsChanged(pair(y = 120f).take(1))
        assertEmpty(s.move(listOf(MultiFingerState.Point(7, 800f, 800f))))
        assertFalse(s.finish(150L))
    }

    @Test fun movingRemainingFingerInvalidatesTap() {
        val s = state()
        s.contactsChanged(pair().take(1))
        assertEmpty(s.move(listOf(MultiFingerState.Point(7, 80f, 100f))))
        assertFalse(s.finish(150L))
    }

    @Test fun addingFingerAgainAfterLiftDoesNotStartAnotherGesture() {
        val s = state()
        s.contactsChanged(pair().take(1))
        s.contactsChanged(pair())
        assertEmpty(s.move(pair(y = 200f)))
        assertFalse(s.finish(150L))
    }

    @Test fun movingOutAndBackCannotBecomeTap() {
        val s = state()
        assertEmpty(s.move(listOf(MultiFingerState.Point(7, 40f, 100f), MultiFingerState.Point(19, 150f, 100f))))
        s.move(pair())
        assertFalse(s.finish(150L))
    }

    @Test fun cancelAndNextGestureClearScaleAndTapState() {
        val s = state()
        s.move(pair(span = 125f))
        s.cancel()
        assertFalse(s.finish(150L))
        s.begin(pair(), 200L, true)
        assertTrue(s.finish(300L))
    }

    @Test fun tinyInitialSpanDoesNotExplodeZoom() {
        val s = MultiFingerState(8f)
        s.begin(pair(span = 0f), 0L, true)
        assertEmpty(s.move(pair(span = 100f)))
        assertFalse(s.finish(150L))
    }
}
