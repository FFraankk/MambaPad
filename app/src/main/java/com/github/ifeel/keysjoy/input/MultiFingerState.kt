package com.github.ifeel.keysjoy.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ceil
import kotlin.math.floor

/** Owns an entire multi-contact chain, including the final lone finger. Coordinates are dp. */
class MultiFingerState(private val slop: Float, private val tapTimeoutMs: Long = 250L) {
    data class Point(val id: Int, val x: Float, val y: Float)
    data class Output(val dx: Float = 0f, val dy: Float = 0f, val zoom: Int = 0)
    enum class Mode { PENDING, SCROLL, PINCH, DRAIN, BLOCKED }
    var mode = Mode.BLOCKED
        private set
    private var initial = emptyMap<Int, Point>()
    private var downTime = 0L
    private var tapEligible = false
    private var startX = 0f
    private var startY = 0f
    private var startSpan = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var previousSpan = 0f
    private var zoomRemainder = 0.0

    fun begin(points: List<Point>, firstDownTime: Long, allowTap: Boolean) {
        cancel()
        if (points.size != 2 || points.map { it.id }.distinct().size != 2) return
        initial = points.associateBy { it.id }
        downTime = firstDownTime
        tapEligible = allowTap
        mode = Mode.PENDING
        startX = (points[0].x + points[1].x) / 2f
        startY = (points[0].y + points[1].y) / 2f
        startSpan = hypot(points[0].x - points[1].x, points[0].y - points[1].y)
        previousX = startX
        previousY = startY
        previousSpan = startSpan
    }

    fun cancel() {
        mode = Mode.BLOCKED
        initial = emptyMap()
        tapEligible = false
        zoomRemainder = 0.0
    }

    /** Adding a third contact or replacing a finger never falls back into a two-finger tap. */
    fun contactsChanged(points: List<Point>) {
        if (mode == Mode.BLOCKED) return
        if (points.size != 1 || points[0].id !in initial) {
            cancel()
            return
        }
        mode = Mode.DRAIN
        invalidateTapIfMoved(points)
    }

    private fun invalidateTapIfMoved(points: List<Point>) {
        if (points.any { p -> initial[p.id]?.let { hypot(p.x - it.x, p.y - it.y) > slop } != false }) {
            tapEligible = false
        }
    }

    fun move(points: List<Point>): Output {
        if (mode == Mode.BLOCKED) return Output()
        invalidateTapIfMoved(points)
        if (mode == Mode.DRAIN) return Output()
        if (points.size != 2 || points.map { it.id }.toSet() != initial.keys) {
            cancel()
            return Output()
        }
        val x = (points[0].x + points[1].x) / 2f
        val y = (points[0].y + points[1].y) / 2f
        val span = hypot(points[0].x - points[1].x, points[0].y - points[1].y)
        if (mode == Mode.PENDING) {
            val pan = hypot(x - startX, y - startY)
            val pinch = if (startSpan > slop * 2) abs(span - startSpan) / 2f else 0f
            mode = when {
                pinch > slop && (pinch > pan * 1.3f || pinch > slop * 2 && pinch >= pan) -> Mode.PINCH
                pan > slop && (pan > pinch * 1.3f || pan > slop * 2 && pan > pinch) -> Mode.SCROLL
                else -> Mode.PENDING
            }
            if (mode == Mode.PENDING) return Output()
            tapEligible = false
        }
        val result = when (mode) {
            Mode.SCROLL -> Output(dx = x - previousX, dy = y - previousY)
            Mode.PINCH -> {
                if (span > slop && previousSpan > slop) {
                    // Accumulate relative scale changes so slow pinches are not rounded away.
                    zoomRemainder += ln(span.toDouble() / previousSpan) / ln(1.1)
                }
                val whole = if (zoomRemainder >= 0) floor(zoomRemainder + 1e-6).toInt()
                    else ceil(zoomRemainder - 1e-6).toInt()
                zoomRemainder -= whole
                Output(zoom = whole.coerceIn(-8, 8))
            }
            else -> Output()
        }
        previousX = x
        previousY = y
        previousSpan = span
        return result
    }

    /** Right click only once, after every finger has lifted, never on cancel or long hold. */
    fun finish(now: Long): Boolean {
        val click = tapEligible && now - downTime in 0..tapTimeoutMs &&
            (mode == Mode.PENDING || mode == Mode.DRAIN)
        cancel()
        return click
    }
}
