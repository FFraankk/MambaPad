package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test

class DragPointerTrackerTest {
    private fun p(id: Int, x: Float, y: Float) = DragPointerTracker.Point(id, x, y)
    @Test fun followsIdEvenWhenPointerIndexesSwap() {
        val t = DragPointerTracker()
        t.rebase(listOf(p(7, 400f, 900f)))
        t.rebase(listOf(p(7, 400f, 900f), p(19, 100f, 100f)))
        assertEquals(DragPointerTracker.Delta(0f, 10f), t.move(listOf(p(19, 100f, 80f), p(7, 400f, 910f))))
    }
    @Test fun primaryLiftRebasesOtherFingerWithoutTeleport() {
        val t = DragPointerTracker()
        t.rebase(listOf(p(7, 400f, 910f), p(19, 100f, 80f)))
        t.rebase(listOf(p(19, 100f, 80f)))
        assertEquals(DragPointerTracker.Delta(0f, 10f), t.move(listOf(p(19, 100f, 90f))))
    }
    @Test fun missingPointerNeverUsesOldCoordinates() {
        val t = DragPointerTracker()
        t.rebase(listOf(p(7, 400f, 910f)))
        assertNull(t.move(listOf(p(19, 100f, 80f))))
        assertEquals(DragPointerTracker.Delta(0f, 10f), t.move(listOf(p(19, 100f, 90f))))
    }
    @Test fun newTouchStartsWithFreshBaseline() {
        val t = DragPointerTracker()
        t.rebase(listOf(p(7, 400f, 910f)))
        t.clear()
        assertNull(t.move(listOf(p(7, 10f, 10f))))
        assertEquals(DragPointerTracker.Delta(1f, 1f), t.move(listOf(p(7, 11f, 11f))))
    }
}
