// Modified for MambaPad (2026), based on KeysJoy. See LICENSE.
package com.github.ifeel.keysjoy.listeners

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.github.ifeel.keysjoy.ui.TouchFeedback
import com.github.ifeel.keysjoy.input.TapDragState
import androidx.core.view.GestureDetectorCompat
import com.github.ifeel.keysjoy.senders.RelativeMouseSender
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.input.DragPointerTracker
import kotlin.math.roundToInt

/**
 * Touch listener that translates finger gestures into HID mouse reports.
 *
 * Architecture modelled after Mousedroid GestureHandler:
 * - GestureDetector.onScroll: uses e1/e2.pointerCount to distinguish single/dual-finger
 *   * single-finger = mouse move with sub-pixel accumulation + speed coefficient
 *   * dual-finger   = HID scroll (unless ScaleGestureDetector is in progress)
 * - onSingleTapUp + activePointerCount: distinguish left-click (1 finger) vs right-click (≥2)
 * - Plain movement/holding never presses a mouse button.
 * - Tap then second-tap-and-slide enters drag; quick double tap double-clicks.
 * - ScaleGestureDetector: pinch-to-zoom (future: wire to action)
 * - onTouch: dispatches to both detectors + tracks pointer state
 *
 * Wheel mode (Scheme C): onTouch branches at entry — Wheel mode bypasses GestureDetector
 * and ScaleGestureDetector entirely, handling touch events as pure scroll via handleWheelTouch.
 */
class ViewListener(val rMouseSender: MouseInputSender) : View.OnTouchListener {
    private val dragPointer = DragPointerTracker()
    private val taps=TapDragState()
    private val tapHandler=Handler(Looper.getMainLooper())
    private val confirmTap=Runnable { emit(taps.confirmSingle()) }
    private var secondStartX=0f
    private var secondStartY=0f
    private val dragActive get() = taps.dragging || buttonHeld
    private fun emit(actions:List<TapDragState.Action>) {
        actions.forEach { when(it) {
            TapDragState.Action.CLICK->rMouseSender.sendTestClick()
            TapDragState.Action.DOWN->rMouseSender.sendLeftClickOn()
            TapDragState.Action.UP->rMouseSender.sendLeftClickOff()
        } }
    }
    private fun pointerPoints(event: MotionEvent, excludeLifted: Boolean = false) =
        (0 until event.pointerCount)
            .filter { !excludeLifted || it != event.actionIndex }
            .map { DragPointerTracker.Point(event.getPointerId(it), event.getX(it), event.getY(it)) }
    // Preserve existing Bluetooth call sites while the gesture layer uses an interface.
    constructor(hidDevice: BluetoothHidDevice, host: BluetoothDevice, sender: RelativeMouseSender) : this(sender)

    fun cancelAndRelease() {
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestureDetector?.onTouchEvent(cancel)
        scaleDetector?.onTouchEvent(cancel)
        cancel.recycle()
        wakeHandler.removeCallbacksAndMessages(null)
        wakeRunnables.clear()
        tapHandler.removeCallbacks(confirmTap)
        emit(taps.cancel())
        buttonHeld = false
        wheelMode = false
        activePointerCount = 0
        scaled = false
        scrolled = false
        rightClickSent = false
        resetAccumulators()
        dragPointer.clear()
        gestureDetector = null
        scaleDetector = null
        touchView = null
        rMouseSender.releaseAll()
    }

    // ==================== Haptics ====================

    var onHaptic: (View) -> Unit = { TouchFeedback.perform(it) }
    private var touchView: View? = null

    // ==================== State ====================

    /** Number of pointers currently on screen. Updated in onTouch before detector dispatch. */
    private var activePointerCount = 0

    /** True while a scale (pinch) gesture is in progress — suppresses two-finger scroll. */
    private var scaled = false

    /** True when a two-finger onScroll has been sent — suppresses spurious right-click on lift. */
    private var scrolled = false

    /** True when a right-click has been sent via ACTION_POINTER_UP — suppresses ghost left-click in onSingleTapUp. */
    private var rightClickSent = false

    /** True when the toolbar Left button is being held — drag-mode indicator. */
    private var buttonHeld = false

    /** Touch coordinates from the last event, for drag delta calculation in ACTION_MOVE. */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ==================== Multi-shot wake ====================

    private val wakeHandler = Handler(Looper.getMainLooper())
    private val wakeRunnables = mutableListOf<Runnable>()

    // ==================== Gesture detectors (lazy-init on first touch) ====================

    private var gestureDetector: GestureDetectorCompat? = null
    private var scaleDetector: ScaleGestureDetector? = null

    // ==================== Speed coefficients (set externally) ====================

    var mouseSpeed: Float = 0.4f
    var scrollSpeed: Float = 0.33f
    var rampEnabled: Boolean = true

    /** Called by SelectDeviceActivity when the Left toolbar button is pressed/released. */
    fun setButtonHeld(held: Boolean) {
        buttonHeld = held
    }

    // ==================== Wheel mode ====================

    /** True while the Wheel button is held — single-finger movement becomes scroll. */
    private var wheelMode = false
    private val wheelPointer = DragPointerTracker()
    private val wheelButtonPointer = DragPointerTracker()

    /** Called by SelectDeviceActivity when the Wheel toolbar button is pressed/released. */
    fun setWheelMode(enabled: Boolean) {
        if (enabled && dragActive) return
        if (enabled) {
            tapHandler.removeCallbacks(confirmTap)
            emit(taps.cancel())
            wakeHandler.removeCallbacksAndMessages(null)
            val now=android.os.SystemClock.uptimeMillis()
            val cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0f,0f,0)
            gestureDetector?.onTouchEvent(cancel)
            scaleDetector?.onTouchEvent(cancel)
            cancel.recycle()
            gestureDetector=null;scaleDetector=null
        }
        wheelMode = enabled
        wheelPointer.clear()
        wheelButtonPointer.clear()
        resetAccumulators()
    }

    // ==================== Speed ramp (smooth acceleration on touch-down) ====================

    private var touchDownTime = 0L
    private val RAMP_DURATION_MS = 400L           // ramp duration
    private val RAMP_START_FRACTION = 0.15f        // starting speed fraction (15%)

    /**
     * Calculate current speed ramp coefficient (0.15 → 1.0, linear ramp over RAMP_DURATION_MS).
     * Called every frame after touch-down; coefficient grows linearly with elapsed time.
     */
    private fun getRampMultiplier(skipRamp: Boolean = false): Float {
        if (skipRamp || !rampEnabled) return 1.0f
        val elapsed = System.currentTimeMillis() - touchDownTime
        if (elapsed >= RAMP_DURATION_MS) return 1.0f
        return RAMP_START_FRACTION + (1.0f - RAMP_START_FRACTION) * (elapsed.toFloat() / RAMP_DURATION_MS)
    }

    // ==================== Sub-pixel accumulators ====================

    private var accumMouseX = 0f
    private var accumMouseY = 0f
    private var accumScrollX = 0f
    private var accumScrollY = 0f

    private fun resetAccumulators() {
        accumMouseX = 0f
        accumMouseY = 0f
        accumScrollX = 0f
        accumScrollY = 0f
    }

    // ==================== onTouch — entry point ====================

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null || v == null) return false

        touchView = v
        // Lazy-init detectors
        if (gestureDetector == null) {
            gestureDetector = GestureDetectorCompat(v.context, GestureListener()).apply { setIsLongpressEnabled(false) }
        }
        if (scaleDetector == null) {
            scaleDetector = ScaleGestureDetector(v.context, ScaleListener())
        }

        // Wheel mode: pure MotionEvent handling, bypass GestureDetector & ScaleDetector
        if (wheelMode) {
            handleWheelTouch(event)
            return true
        }

        // Normal mode: GestureDetector + ScaleDetector
        handleNormalTouch(v, event)
        return true
    }

    // ==================== Wheel mode touch handling ====================

    /**
     * Pure MotionEvent → scroll translation.
     * Wheel mode bypasses GestureDetector/ScaleDetector entirely.
     * Single-finger movement maps directly to HID scroll.
     */
    /** Direct dragging on the wheel button uses its own coordinate baseline. */
    fun onWheelButtonTouch(event: MotionEvent) {
        if(wheelMode)handleWheelTouch(event,wheelButtonPointer)
    }
    private fun handleWheelTouch(event: MotionEvent, pointer: DragPointerTracker = wheelPointer) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer.rebase(pointerPoints(event))
                lastTouchX = event.x
                lastTouchY = event.y
                resetAccumulators()
            }
            MotionEvent.ACTION_MOVE -> {
                val delta=pointer.move(pointerPoints(event))?:return
                val dx = delta.x
                val dy = delta.y
                accumScrollX += -dx * scrollSpeed  // invert: finger move → content scroll
                accumScrollY += -dy * scrollSpeed
                val sx = accumScrollX.roundToInt()
                val sy = accumScrollY.roundToInt()
                if (sx != 0 || sy != 0) {
                    rMouseSender.sendScroll(sy, sx)
                    accumScrollX -= sx
                    accumScrollY -= sy
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> pointer.rebase(pointerPoints(event))
            MotionEvent.ACTION_POINTER_UP -> pointer.rebase(pointerPoints(event,excludeLifted=true))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointer.clear()
                resetAccumulators()
            }
        }
    }

    // ==================== Normal mode touch handling ====================

    /**
     * Pointer-state tracking + GestureDetector/ScaleDetector dispatch.
     * Extracted from the original onTouch body.
     */
    private fun handleNormalTouch(v: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerCount = event.pointerCount
                dragPointer.clear()
                dragPointer.rebase(pointerPoints(event))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                dragPointer.rebase(pointerPoints(event))
            }
            MotionEvent.ACTION_MOVE -> {
                // Every drag uses one pointer-ID tracker and one movement path.
                // GestureDetector's centroid and double-tap callbacks must not also move it.
                if(taps.secondPressed && !taps.dragging && event.pointerCount==1) {
                    val dx=event.x-secondStartX;val dy=event.y-secondStartY
                    val slop=ViewConfiguration.get(v.context).scaledTouchSlop
                    if(dx*dx+dy*dy>slop*slop) {
                        emit(taps.startDrag())
                        onHaptic(v)
                    }
                }
                // Preserve the initial baseline until drag classification completes.
                val delta=if(taps.secondPressed&&!taps.dragging)null else dragPointer.move(pointerPoints(event))
                if (dragActive && delta != null) {
                    accumMouseX += delta.x * mouseSpeed
                    accumMouseY += delta.y * mouseSpeed
                    var mx = Math.round(accumMouseX)
                    var my = Math.round(accumMouseY)
                    if (mx != 0 || my != 0) {
                        rMouseSender.sendMouseMove(mx, my)
                        accumMouseX -= mx
                        accumMouseY -= my
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                dragPointer.rebase(pointerPoints(event, excludeLifted = true))
                // Right-click on two-finger lift — but ONLY when the user was NOT
                // pinching (scaled) or scrolling (scrolled).  The gesture detector's
                // onSingleTapUp handles the static two-finger tap; this handles the
                // case where the second finger lifts after a brief touch.
                if (event.pointerCount == 2 && !scaled && !scrolled && !dragActive && !taps.secondPressed) {
                    val fingerDistance = Math.hypot(
                        (event.getX(0) - event.getX(1)).toDouble(),
                        (event.getY(0) - event.getY(1)).toDouble()
                    ).toFloat()
                    if (fingerDistance > 30f) {
                        Log.i("ViewListener", "Two-finger right click (POINTER_UP)")
                        onHaptic(v)
                        rMouseSender.sendRightClick()
                        rightClickSent = true
                    }
                }
                activePointerCount = event.pointerCount
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                wakeHandler.removeCallbacksAndMessages(null)
                wakeRunnables.clear()

                if(event.actionMasked==MotionEvent.ACTION_CANCEL) {
                    tapHandler.removeCallbacks(confirmTap)
                    emit(taps.cancel())
                } else {
                    if(taps.secondPressed && !taps.dragging) onHaptic(v)
                    emit(taps.secondUp())
                }
                // Keep tap-suppression flags until GestureDetector consumes this UP.
                resetAccumulators()
                dragPointer.clear()
            }
        }

        // Dispatch to both detectors — Mousedroid pattern
        scaleDetector?.onTouchEvent(event)
        gestureDetector?.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activePointerCount = 0
            scaled = false
            scrolled = false
            rightClickSent = false
        }
    }

    // ===================== GestureDetector Listener =====================

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            Log.d("ViewListener", "onDown: sending multi-shot wake")

            // Flush a preceding single click before an unrelated new movement.
            if(!taps.secondPressed) {
                tapHandler.removeCallbacks(confirmTap)
                emit(taps.confirmSingle())
            }
            // Reset gesture-chain state
            scaled = false
            scrolled = false

            // Record initial touch position for drag delta calculation
            lastTouchX = e.x
            lastTouchY = e.y

            // Record gesture start time for speed ramp
            touchDownTime = System.currentTimeMillis()

            // Reset sub-pixel accumulators at the start of every gesture chain
            resetAccumulators()

            // Multi-shot wake: 4 zero-reports at 5ms intervals.
            // Skip when buttonHeld — reset() would clear leftButton mid-drag.
            if (!buttonHeld) {
                wakeHandler.removeCallbacksAndMessages(null)
                wakeRunnables.clear()
                for (i in 0 until 4) {
                    val runnable = Runnable {
                        rMouseSender.wake()
                    }
                    wakeRunnables.add(runnable)
                    wakeHandler.postDelayed(runnable, i * 5L)
                }
            }
            // Must return true to claim the event chain
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (dragActive || taps.secondPressed) return true
            // Cancel pending multi-shot wake runnables once scrolling starts
            for (r in wakeRunnables) {
                wakeHandler.removeCallbacks(r)
            }
            wakeRunnables.clear()

            // Determine pointer count from the scroll events (Mousedroid pattern).
            // e1 may be null on the very first scroll; e2 always holds current state.
            val ptrCount = maxOf(e1?.pointerCount ?: 1, e2.pointerCount)

            when {
                // ---------- Two-finger scroll ----------
                ptrCount >= 2 -> {
                    if (scaleDetector?.isInProgress == true) {
                        // Pinch-to-zoom active — let ScaleListener handle it;
                        // skip scroll to avoid conflicting HID reports.
                        return true
                    }
                    scrolled = true
                    val ramp = getRampMultiplier()
                    accumScrollY += -distanceY * scrollSpeed * ramp
                    accumScrollX += -distanceX * scrollSpeed * ramp
                    val sy = Math.round(accumScrollY)
                    val sx = Math.round(accumScrollX)
                    if (sy != 0 || sx != 0) {
                        rMouseSender.sendScroll(sy, sx)
                        accumScrollY -= sy
                        accumScrollX -= sx
                    }
                }
                // ---------- Single-finger mouse move ----------
                else -> {
                    val ramp = getRampMultiplier(skipRamp = dragActive)
                    accumMouseX += -distanceX * mouseSpeed * ramp
                    accumMouseY += -distanceY * mouseSpeed * ramp
                    var mx = Math.round(accumMouseX)
                    var my = Math.round(accumMouseY)
                    if (mx != 0 || my != 0) {
                        rMouseSender.sendMouseMove(mx, my)
                        accumMouseX -= mx
                        accumMouseY -= my
                    }
                }
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Button held — suppress tap to prevent unwanted left/right click.
            if (buttonHeld) return true
            // Skip if right-click already sent by ACTION_POINTER_UP — prevents ghost left-click.
            if (rightClickSent) return true
            // Use activePointerCount (tracked in onTouch) — more reliable than
            // e.pointerCount which may report stale values from the event.
            if (activePointerCount >= 2) {
                Log.i("ViewListener", "Two-finger tap → right click")
                touchView?.let(onHaptic)
                rMouseSender.sendRightClick()
                return true
            }

            // Shell needs an ordinary DOWN for dragging, not a double-click DOWN.
            touchView?.let(onHaptic)
            emit(taps.firstTap())
            tapHandler.removeCallbacks(confirmTap)
            tapHandler.postDelayed(confirmTap,ViewConfiguration.getDoubleTapTimeout().toLong())
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            tapHandler.removeCallbacks(confirmTap)
            return true
        }
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if(e.actionMasked==MotionEvent.ACTION_DOWN && !buttonHeld) {
                taps.secondDown()
                secondStartX=e.x;secondStartY=e.y
                lastTouchX=e.x;lastTouchY=e.y
            }
            // UP/CANCEL is completed by the common path before detector dispatch.
            return true
        }
    }

    // ===================== ScaleGestureDetector Listener =====================

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (dragActive || taps.secondPressed) return false
            scaled = true
            Log.d("ViewListener", "Scale begin: span=${detector.currentSpan}")
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (dragActive || taps.secondPressed) return true
            // Pinch-to-zoom: send Ctrl+scroll as zoom action.
            val factor = detector.scaleFactor
            val scrollDelta = Math.round((factor - 1.0f) * 10)
            if (scrollDelta != 0) {
                rMouseSender.sendScroll(scrollDelta, 0)
            }
            Log.d("ViewListener", "Scale: factor=$factor scrollDelta=$scrollDelta")
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaled = false
            Log.d("ViewListener", "Scale end")
        }
    }
}
