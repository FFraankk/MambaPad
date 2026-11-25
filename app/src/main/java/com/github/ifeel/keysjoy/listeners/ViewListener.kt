// Modified for MambaPad (2026), based on KeysJoy. See LICENSE.
package com.github.ifeel.keysjoy.listeners

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.github.ifeel.keysjoy.ui.TouchFeedback
import com.github.ifeel.keysjoy.input.TapDragState
import androidx.core.view.GestureDetectorCompat
import com.github.ifeel.keysjoy.senders.RelativeMouseSender
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.input.DragPointerTracker
import com.github.ifeel.keysjoy.input.MultiFingerState
import com.github.ifeel.keysjoy.input.MultiFingerSwipeState
import com.github.ifeel.keysjoy.input.EdgeMotionState
import kotlin.math.roundToInt

/** Single-finger tap/drag keeps the system double-tap detector. Multi-contact
 * chains have one exclusive, pointer-ID-based owner until all contacts lift. */
class ViewListener(val rMouseSender: MouseInputSender) : View.OnTouchListener {
    private val dragPointer = DragPointerTracker()
    private val edgeMotion = EdgeMotionState()
    private val edgeHandler = Handler(Looper.getMainLooper())
    private var edgeWidth = 0
    private var edgeHeight = 0
    private var leftButtonHeld = false
    private var edgeAccumX = 0f
    private var edgeAccumY = 0f
    private val edgeFrame = object : Runnable {
        override fun run() {
            val view = touchView
            if (view == null || !view.isAttachedToWindow || !view.isShown ||
                view.width != edgeWidth || view.height != edgeHeight || wheelMode) {
                stopEdgeMotion()
                return
            }
            val delta = edgeMotion.tick(android.os.SystemClock.uptimeMillis())
            edgeAccumX += delta.x
            edgeAccumY += delta.y
            val dx = edgeAccumX.roundToInt()
            val dy = edgeAccumY.roundToInt()
            if (dx != 0 || dy != 0) {
                // Preserve current button state: ordinary movement never presses a button.
                rMouseSender.sendMouseMove(dx, dy)
                edgeAccumX -= dx
                edgeAccumY -= dy
            }
            if (edgeMotion.armed) edgeHandler.postDelayed(this, 16L)
        }
    }

    private fun stopEdgeMotion() {
        edgeHandler.removeCallbacks(edgeFrame)
        edgeMotion.stop()
        edgeAccumX = 0f
        edgeAccumY = 0f
    }
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

    private fun cancelSingleDetector() {
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestureDetector?.onTouchEvent(cancel)
        cancel.recycle()
        gestureDetector = null
        wakeHandler.removeCallbacksAndMessages(null)
        wakeRunnables.clear()
    }

    fun cancelAndRelease() {
        stopEdgeMotion()
        cancelSingleDetector()
        tapHandler.removeCallbacks(confirmTap)
        emit(taps.cancel())
        multiGesture?.cancel()
        multiGesture = null
        swipeGesture?.cancel()
        swipeGesture = null
        suppressDetector = false
        buttonHeld = false
        leftButtonHeld = false
        wheelMode = false
        resetAccumulators()
        dragPointer.clear()
        wheelPointer.clear()
        wheelButtonPointer.clear()
        touchView = null
        rMouseSender.releaseAll()
    }

    // ==================== Haptics ====================

    var onHaptic: (View) -> Unit = { TouchFeedback.perform(it) }
    private var touchView: View? = null

    private var buttonHeld = false
    private var multiGesture: MultiFingerState? = null
    private var swipeGesture: MultiFingerSwipeState? = null
    private var suppressDetector = false
    private var singleMoved = false
    private var firstDownTime = 0L

    // ==================== Multi-shot wake ====================

    private val wakeHandler = Handler(Looper.getMainLooper())
    private val wakeRunnables = mutableListOf<Runnable>()

    // ==================== Gesture detectors (lazy-init on first touch) ====================

    private var gestureDetector: GestureDetectorCompat? = null

    // ==================== Speed coefficients (set externally) ====================

    var mouseSpeed: Float = 0.4f
    var scrollSpeed: Float = 0.33f
    var rampEnabled: Boolean = true

    /** Called by SelectDeviceActivity when the Left toolbar button is pressed/released. */
    fun setButtonHeld(held: Boolean, leftHeld: Boolean = held) {
        if (!leftHeld) stopEdgeMotion()
        if (held) multiGesture?.cancel()
        if (held) swipeGesture?.cancel()
        buttonHeld = held
        leftButtonHeld = leftHeld
    }

    // ==================== Wheel mode ====================

    /** True while the Wheel button is held — single-finger movement becomes scroll. */
    private var wheelMode = false
    private val wheelPointer = DragPointerTracker()
    private val wheelButtonPointer = DragPointerTracker()

    /** Called by SelectDeviceActivity when the Wheel toolbar button is pressed/released. */
    fun setWheelMode(enabled: Boolean) {
        if (enabled && dragActive) return
        stopEdgeMotion()
        cancelSingleDetector()
        tapHandler.removeCallbacks(confirmTap)
        emit(taps.cancel())
        multiGesture?.cancel()
        multiGesture = null
        swipeGesture?.cancel()
        swipeGesture = null
        suppressDetector = true
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN && gestureDetector == null) {
            gestureDetector = GestureDetectorCompat(v.context, GestureListener()).apply { setIsLongpressEnabled(false) }
        }
        // Wheel mode bypasses gesture recognition.
        if (wheelMode) {
            handleWheelTouch(event)
            return true
        }

        // Exclusive single-finger or multi-finger handling
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

    private fun multiPoints(v: View, event: MotionEvent, excludeLifted: Boolean = false): List<MultiFingerState.Point> {
        val density = v.resources.displayMetrics.density
        return (0 until event.pointerCount)
            .filter { !excludeLifted || it != event.actionIndex }
            .map { MultiFingerState.Point(event.getPointerId(it), event.getX(it) / density, event.getY(it) / density) }
    }

    private fun moveMouse(dx: Float, dy: Float) {
        accumMouseX += dx
        accumMouseY += dy
        val mx = accumMouseX.roundToInt()
        val my = accumMouseY.roundToInt()
        if (mx != 0 || my != 0) {
            rMouseSender.sendMouseMove(mx, my)
            accumMouseX -= mx
            accumMouseY -= my
        }
    }

    private fun emitMulti(v: View, output: MultiFingerState.Output) {
        val scale = v.resources.displayMetrics.density * scrollSpeed
        accumScrollX += output.dx * scale
        accumScrollY += output.dy * scale
        val sx = accumScrollX.roundToInt()
        val sy = accumScrollY.roundToInt()
        if (sx != 0 || sy != 0) {
            rMouseSender.sendScroll(sy, sx)
            accumScrollX -= sx
            accumScrollY -= sy
        }
        if (output.zoom != 0) rMouseSender.sendZoom(output.zoom)
    }

    private fun handleNormalTouch(v: View, event: MotionEvent) {
        val wasDrag = dragActive
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopEdgeMotion()
                multiGesture = null
                swipeGesture = null
                suppressDetector = false
                singleMoved = false
                firstDownTime = event.eventTime
                edgeWidth = v.width
                edgeHeight = v.height
                val density = v.resources.displayMetrics.density
                edgeMotion.begin(event.x / density, event.y / density,
                    v.width / density, v.height / density, event.eventTime)
                dragPointer.rebase(pointerPoints(event))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                stopEdgeMotion()
                dragPointer.rebase(pointerPoints(event))
                if (!dragActive) {
                    val points = multiPoints(v, event)
                    if (swipeGesture != null) swipeGesture?.contactsAdded(points)
                    else if (event.pointerCount == 3 && (multiGesture == null || multiGesture?.mode == MultiFingerState.Mode.PENDING)) {
                        swipeGesture = MultiFingerSwipeState().apply { begin(points) }
                    }
                    if (multiGesture == null) {
                        val slop = ViewConfiguration.get(v.context).scaledTouchSlop / v.resources.displayMetrics.density
                        multiGesture = MultiFingerState(slop).apply {
                            begin(multiPoints(v, event), firstDownTime, !singleMoved)
                        }
                    } else multiGesture?.contactsChanged(multiPoints(v, event))
                    tapHandler.removeCallbacks(confirmTap)
                    emit(taps.cancel())
                }
                // Existing drags keep their pointer tracker and button state; all other
                // chains belong exclusively to MultiFingerState until the final UP.
                suppressDetector = true
                cancelSingleDetector()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                stopEdgeMotion()
                swipeGesture?.let {
                    it.move(multiPoints(v, event))
                    it.contactsLifted(multiPoints(v, event, excludeLifted = true))
                }
                multiGesture?.let {
                    emitMulti(v, it.move(multiPoints(v, event)))
                    it.contactsChanged(multiPoints(v, event, excludeLifted = true))
                }
                dragPointer.rebase(pointerPoints(event, excludeLifted = true))
            }
            MotionEvent.ACTION_MOVE -> {
                val multi = multiGesture
                if (swipeGesture != null) {
                    swipeGesture?.move(multiPoints(v, event))
                } else if (multi != null) {
                    emitMulti(v, multi.move(multiPoints(v, event)))
                } else {
                    if (taps.secondPressed && !taps.dragging && event.pointerCount == 1) {
                        val dx = event.x - secondStartX
                        val dy = event.y - secondStartY
                        val slop = ViewConfiguration.get(v.context).scaledTouchSlop
                        if (dx * dx + dy * dy > slop * slop) {
                            emit(taps.startDrag())
                            onHaptic(v)
                        }
                    }
                    val delta = if (taps.secondPressed && !taps.dragging) null else dragPointer.move(pointerPoints(event))
                    if (dragActive && delta != null) moveMouse(delta.x * mouseSpeed, delta.y * mouseSpeed)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopEdgeMotion()
                wakeHandler.removeCallbacksAndMessages(null)
                wakeRunnables.clear()
                val multi = multiGesture
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    tapHandler.removeCallbacks(confirmTap)
                    emit(taps.cancel())
                    multi?.cancel()
                    swipeGesture?.cancel()
                    cancelSingleDetector()
                } else if (multi != null) {
                    multi.move(multiPoints(v, event))
                    val shortcut = swipeGesture?.finish()
                    if (shortcut != null) {
                        multi.cancel()
                        onHaptic(v)
                        rMouseSender.sendShortcut(shortcut)
                    } else if (multi.finish(event.eventTime)) {
                        onHaptic(v)
                        rMouseSender.sendRightClick()
                    }
                } else {
                    if (taps.secondPressed && !taps.dragging) onHaptic(v)
                    emit(taps.secondUp())
                    if (wasDrag) cancelSingleDetector()
                }
            }
        }

        if (!suppressDetector && multiGesture == null) gestureDetector?.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !suppressDetector) {
            val density = v.resources.displayMetrics.density
            val wasArmed = edgeMotion.armed
            edgeMotion.move(event.x / density, event.y / density, event.eventTime, taps.dragging || leftButtonHeld)
            if (wasArmed && !edgeMotion.armed) {
                edgeHandler.removeCallbacks(edgeFrame)
                edgeAccumX = 0f
                edgeAccumY = 0f
            } else if (!wasArmed && edgeMotion.armed) edgeHandler.postDelayed(edgeFrame, 16L)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            multiGesture = null
            swipeGesture = null
            suppressDetector = false
            resetAccumulators()
            dragPointer.clear()
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
            if (dragActive || taps.secondPressed || suppressDetector || e2.pointerCount != 1) return true
            for (r in wakeRunnables) wakeHandler.removeCallbacks(r)
            wakeRunnables.clear()
            singleMoved = true
            val speed = mouseSpeed * getRampMultiplier()
            moveMouse(-distanceX * speed, -distanceY * speed)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (buttonHeld || suppressDetector || multiGesture != null) return true
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
            }
            // UP/CANCEL is completed by the common path before detector dispatch.
            return true
        }
    }

}
