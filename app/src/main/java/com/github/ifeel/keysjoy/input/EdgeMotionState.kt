package com.github.ifeel.keysjoy.input

import kotlin.math.hypot

/** Drag-only edge continuation. Coordinates are dp; output is relative mouse units. */
class EdgeMotionState {
    data class Delta(val x: Float, val y: Float)
    private data class Sample(val time: Long, val dx: Float, val dy: Float)
    private val samples = mutableListOf<Sample>()
    private var width = 0f
    private var height = 0f
    private var x = 0f
    private var y = 0f
    private var contact = false
    private var fromInterior = false
    private var sides = 0
    private var direction = Delta(0f, 0f)
    private var lastTick = 0L
    val armed get() = sides != 0

    fun stop() {
        contact = false
        fromInterior = false
        sides = 0
        samples.clear()
        direction = Delta(0f, 0f)
    }

    fun begin(x: Float, y: Float, width: Float, height: Float, now: Long) {
        stop()
        this.width = width
        this.height = height
        this.x = x
        this.y = y
        contact = width > 48f && height > 48f
        fromInterior = edgeSides(x, y, 16f) == 0
        lastTick = now
    }

    fun move(x: Float, y: Float, now: Long, canArm: Boolean): Boolean {
        if (!contact) return false
        if (!canArm && armed) {
            sides = 0
            fromInterior = false
            samples.clear()
        }
        val dx = x - this.x
        val dy = y - this.y
        // Ignore sensor jitter, but retain the baseline so slow deliberate motion accumulates.
        if (hypot(dx, dy) < 0.75f) return false
        this.x = x
        this.y = y
        if (armed && ((edgeSides(x, y, 24f) and sides) == 0 ||
                (sides and LEFT != 0 && dx > 0.75f) ||
                (sides and RIGHT != 0 && dx < -0.75f) ||
                (sides and TOP != 0 && dy > 0.75f) ||
                (sides and BOTTOM != 0 && dy < -0.75f) ||
                dx * direction.x + dy * direction.y < -0.75f)) {
            sides = 0
            samples.clear()
        }
        samples.removeAll { now - it.time > 100L }
        // A deliberate turn starts a fresh direction estimate. Otherwise an inward
        // reposition and equally sized outward stroke can cancel at the edge.
        val recentX = samples.sumOf { it.dx.toDouble() }.toFloat()
        val recentY = samples.sumOf { it.dy.toDouble() }.toFloat()
        if (recentX * dx + recentY * dy < 0f) samples.clear()
        samples.add(Sample(now, dx, dy))
        val edge = edgeSides(x, y, 16f)
        if (edge == 0) fromInterior = true
        if (!armed && fromInterior && canArm) {
            val sx = samples.sumOf { it.dx.toDouble() }.toFloat()
            val sy = samples.sumOf { it.dy.toDouble() }.toFloat()
            // Both the recent direction and latest movement must head into this edge.
            val outward = (if (sx < 0f && dx < 0f) LEFT else 0) or
                (if (sx > 0f && dx > 0f) RIGHT else 0) or
                (if (sy < 0f && dy < 0f) TOP else 0) or
                (if (sy > 0f && dy > 0f) BOTTOM else 0)
            val candidate = edge and outward
            val length = hypot(sx, sy)
            if (candidate != 0 && length >= 0.75f) {
                sides = candidate
                direction = Delta(sx / length, sy / length)
                fromInterior = false
                lastTick = now
            }
        }
        return true
    }

    /** Called even without MOVE events. Never catch up a long UI stall in one jump. */
    fun tick(now: Long): Delta {
        val elapsed = (now - lastTick).coerceIn(0L, 32L)
        lastTick = now
        if (!armed) return Delta(0f, 0f)
        val distance = 80f * elapsed / 1000f
        return Delta(direction.x * distance, direction.y * distance)
    }

    private fun edgeSides(x: Float, y: Float, band: Float): Int =
        (if (x <= band) LEFT else 0) or (if (x >= width - band) RIGHT else 0) or
            (if (y <= band) TOP else 0) or (if (y >= height - band) BOTTOM else 0)

    private companion object {
        const val LEFT = 1
        const val RIGHT = 2
        const val TOP = 4
        const val BOTTOM = 8
    }
}
