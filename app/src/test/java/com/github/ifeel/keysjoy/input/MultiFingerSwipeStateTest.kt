package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test

class MultiFingerSwipeStateTest {
    private fun points(count: Int = 3, dx: Float = 0f, dy: Float = 0f) =
        (0 until count).map { MultiFingerState.Point(it + 7, 60f + it * 50 + dx, 100f + dy) }
    private fun swipe(count: Int, dx: Float, dy: Float): TouchpadShortcut? = MultiFingerSwipeState().run {
        begin(points(count)); move(points(count, dx, dy)); contactsLifted(points(count - 1, dx, dy)); finish()
    }

    @Test fun threeFingerDirectionsAndSystemScreenshotExclusion() {
        assertEquals(TouchpadShortcut.TASK_VIEW, swipe(3, 0f, -60f))
        assertEquals(TouchpadShortcut.NEXT_APP, swipe(3, -60f, 0f))
        assertEquals(TouchpadShortcut.PREVIOUS_APP, swipe(3, 60f, 0f))
        assertNull(swipe(3, 0f, 60f))
    }
    @Test fun fourFingerDirections() {
        assertEquals(TouchpadShortcut.NEXT_DESKTOP, swipe(4, -60f, 0f))
        assertEquals(TouchpadShortcut.PREVIOUS_DESKTOP, swipe(4, 60f, 0f))
        assertEquals(TouchpadShortcut.TASK_VIEW, swipe(4, 0f, -60f))
        assertEquals(TouchpadShortcut.SHOW_DESKTOP, swipe(4, 0f, 60f))
    }
    @Test fun shortOrAmbiguousDiagonalSwipeDoesNothing() {
        assertNull(swipe(3, 30f, 0f))
        assertNull(swipe(4, 60f, 60f))
        assertNull(swipe(4, 0f, 0f))
    }
    @Test fun returningToStartCancelsPendingAction() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dx = -80f)); s.move(points(dx = -5f))
        assertNull(s.finish())
    }
    @Test fun staggeredLiftAndRemainingFingerCannotChangeDirection() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dx = -60f))
        s.contactsLifted(points(2, dx = -60f))
        s.move(points(2, dx = 900f, dy = 900f))
        s.contactsLifted(points(1, dx = 900f, dy = 900f))
        assertEquals(TouchpadShortcut.NEXT_APP, s.finish())
        assertNull(s.finish())
    }
    @Test fun indexReorderingDoesNotChangeCentroid() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dx = -60f).reversed())
        assertEquals(TouchpadShortcut.NEXT_APP, s.finish())
    }
    @Test fun fourthFingerRebasesWithoutJumpOrThreeFingerAction() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dx = -60f))
        s.contactsAdded(points(4, dx = -60f))
        assertNull(s.finish())
        s.begin(points()); s.contactsAdded(points(4))
        s.move(points(4, dx = -60f))
        assertEquals(TouchpadShortcut.NEXT_DESKTOP, s.finish())
    }
    @Test fun fifthOrReplacementFingerCancelsEverything() {
        val s = MultiFingerSwipeState()
        s.begin(points(4)); s.move(points(4, dx = -60f)); s.contactsAdded(points(5))
        assertNull(s.finish())
        s.begin(points()); s.move(points(dx = -60f).map { it.copy(id = it.id + 100) })
        assertNull(s.finish())
    }
    @Test fun addingContactAfterLiftCancelsInsteadOfReusingPendingSwipe() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dx = -60f)); s.contactsLifted(points(2))
        s.contactsAdded(points())
        assertNull(s.finish())
    }
    @Test fun systemCancelNeverSendsShortcutAndNextGestureIsFresh() {
        val s = MultiFingerSwipeState()
        s.begin(points()); s.move(points(dy = -80f)); s.cancel()
        assertNull(s.finish())
        s.begin(points(4)); s.move(points(4, dy = 60f))
        assertEquals(TouchpadShortcut.SHOW_DESKTOP, s.finish())
    }
    @Test fun oneMovingFingerOrSpreadCannotBecomeSystemSwipe() {
        val s = MultiFingerSwipeState()
        s.begin(points())
        s.move(points().mapIndexed { i,p -> if(i==0)p.copy(x=p.x+180f) else p })
        assertNull(s.finish())
        s.begin(points(4))
        s.move(points(4).mapIndexed { i,p -> p.copy(x=p.x+(i-1)*80f) })
        assertNull(s.finish())
    }
}
