package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class EdgeMotionStateTest {
    private fun ready(x: Float = 295f, y: Float = 150f): EdgeMotionState =
        EdgeMotionState().apply {
            begin(150f, 150f, 300f, 300f, 0L)
            move(x, y, 100L, true)
        }

    @Test fun allFourEdgesContinueInTheirOriginalDirection() {
        for ((x, y) in listOf(295f to 150f, 5f to 150f, 150f to 5f, 150f to 295f)) {
            val state = ready(x, y)
            assertTrue(state.armed)
            val delta = state.tick(500L)
            assertTrue(delta.x * (x - 150f) + delta.y * (y - 150f) > 0f)
            assertEquals(0f, delta.x * (y - 150f) - delta.y * (x - 150f), 0.001f)
        }
    }

    @Test fun directPressAndMovementWithinEdgeNeverArm() {
        val state = EdgeMotionState()
        state.begin(290f, 150f, 300f, 300f, 0L)
        state.move(295f, 150f, 100L, true)
        assertFalse(state.armed)
        assertEquals(EdgeMotionState.Delta(0f, 0f), state.tick(1000L))
        state.move(250f, 150f, 1100L, true)
        state.move(295f, 150f, 1200L, true)
        assertTrue(state.armed)
    }

    @Test fun centralMovementAndOrdinaryMouseMovementDoNotArm() {
        assertFalse(ready(200f, 180f).armed)
        val state = EdgeMotionState()
        state.begin(150f, 150f, 300f, 300f, 0L)
        state.move(295f, 150f, 100L, false)
        assertFalse(state.armed)
    }

    @Test fun continuationStartsOnFirstFrameWithoutDwellOrRamp() {
        val state = ready()
        assertEquals(0f, state.tick(100L).x, 0f)
        assertEquals(1.28f, state.tick(116L).x, 0.0001f)
        assertEquals(1.28f, state.tick(132L).x, 0.0001f)
    }

    @Test fun diagonalDirectionIsPreservedAtSideAndCorner() {
        for (y in listOf(200f, 295f)) {
            val delta = ready(295f, y).tick(500L)
            assertEquals((y - 150f) / 145f, delta.y / delta.x, 0.0001f)
            assertEquals(2.56f, hypot(delta.x, delta.y), 0.0001f)
        }
    }

    @Test fun latestMovementMustActuallyHeadOutward() {
        val state = EdgeMotionState()
        state.begin(150f, 150f, 300f, 300f, 0L)
        state.move(280f, 150f, 100L, true)
        state.move(280f, 295f, 120L, true)
        val delta = state.tick(500L)
        assertTrue(delta.y > 0f)
        // The recent diagonal approach is retained rather than forced to the edge normal.
        assertTrue(delta.x > 0f)
    }

    @Test fun reverseStopsImmediatelyAndCannotRearmUntilReturningInside() {
        val state = ready()
        state.move(293f, 150f, 500L, true)
        assertFalse(state.armed)
        assertEquals(EdgeMotionState.Delta(0f, 0f), state.tick(900L))
        state.move(295f, 150f, 950L, true)
        assertFalse(state.armed)
        state.move(250f, 150f, 1000L, true)
        state.move(295f, 150f, 1150L, true)
        assertTrue(state.armed)
    }

    @Test fun inwardMotionAtCornerStopsEvenIfOtherAxisStillHeadsOutward() {
        val state = ready(295f, 295f)
        state.move(293f, 298f, 500L, true)
        assertFalse(state.armed)
    }

    @Test fun ongoingOutwardMovementDoesNotInterruptContinuation() {
        val state = ready()
        state.tick(116L)
        state.move(297f, 150f, 120L, true)
        assertEquals(1.28f, state.tick(132L).x, 0.0001f)
    }

    @Test fun zeroEventsAndSensorJitterDoNotEraseDirection() {
        val state = ready()
        assertFalse(state.move(295f, 150f, 200L, true))
        assertFalse(state.move(294.8f, 150.1f, 400L, true))
        assertTrue(state.tick(500L).x > 0f)
        assertEquals(0f, state.tick(516L).y, 0f)
    }

    @Test fun verySlowInwardMovementAccumulatesAndStops() {
        val state = ready()
        state.move(294.6f, 150f, 200L, true)
        state.move(294.2f, 150f, 250L, true)
        assertFalse(state.armed)
    }

    @Test fun oldSamplesDoNotBiasTheNewDirection() {
        val state = EdgeMotionState()
        state.begin(150f, 150f, 300f, 300f, 0L)
        state.move(150f, 250f, 100L, true)
        state.move(295f, 250f, 300L, true)
        assertEquals(0f, state.tick(700L).y, 0f)
    }

    @Test fun stalledFrameIsClampedAndDuplicateTickDoesNotMove() {
        val state = ready()
        assertEquals(2.56f, state.tick(10000L).x, 0.0001f)
        assertEquals(0f, state.tick(10000L).x, 0f)
    }

    @Test fun stopAndNewContactClearAllPendingMotion() {
        val state = ready()
        state.stop()
        state.move(299f, 150f, 500L, true)
        assertFalse(state.armed)
        assertEquals(EdgeMotionState.Delta(0f, 0f), state.tick(1000L))
        state.begin(150f, 150f, 300f, 300f, 1100L)
        assertEquals(EdgeMotionState.Delta(0f, 0f), state.tick(2000L))
    }

    @Test fun zeroOrTinySurfaceDoesNotArm() {
        for (size in listOf(0f, 32f, 48f)) {
            val state = EdgeMotionState()
            state.begin(10f, 10f, size, size, 0L)
            state.move(size, size, 100L, true)
            assertFalse(state.armed)
        }
    }

    @Test fun endingDragStopsEvenWithNoFingerDisplacement() {
        val state = ready()
        state.move(295f, 150f, 120L, false)
        assertFalse(state.armed)
        assertEquals(EdgeMotionState.Delta(0f, 0f), state.tick(132L))
    }
}
