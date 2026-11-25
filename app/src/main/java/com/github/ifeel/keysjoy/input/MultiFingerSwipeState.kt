package com.github.ifeel.keysjoy.input

import kotlin.math.abs

/** Three/four-contact swipes are committed only at final UP, never on system CANCEL. */
class MultiFingerSwipeState(private val distance: Float = 48f) {
    private var ids = emptySet<Int>()
    private var initial = emptyMap<Int, MultiFingerState.Point>()
    private var anchorX = 0f
    private var anchorY = 0f
    private var pending: TouchpadShortcut? = null
    private var draining = false
    private var blocked = true

    fun begin(points: List<MultiFingerState.Point>) {
        cancel()
        if (points.size !in 3..4 || points.map { it.id }.distinct().size != points.size) return
        ids = points.map { it.id }.toSet()
        initial = points.associateBy { it.id }
        anchorX = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        anchorY = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        draining = false
        blocked = false
    }

    /** A fourth finger rebases before any action is sent; a fifth or a replacement cancels. */
    fun contactsAdded(points: List<MultiFingerState.Point>) {
        if (!blocked && !draining && ids.size == 3 && points.size == 4 && points.map { it.id }.toSet().containsAll(ids)) {
            begin(points)
        } else cancel()
    }

    fun move(points: List<MultiFingerState.Point>) {
        if (blocked || draining) return
        if (points.size != ids.size || points.map { it.id }.toSet() != ids) {
            cancel()
            return
        }
        val dx = points.sumOf { it.x.toDouble() }.toFloat() / points.size - anchorX
        val dy = points.sumOf { it.y.toDouble() }.toFloat() / points.size - anchorY
        pending = when {
            abs(dx) >= distance && abs(dx) > abs(dy) * 1.3f && points.all {
                (it.x - initial.getValue(it.id).x) * (if (dx < 0) -1 else 1) >= distance / 2
            } ->
                if (ids.size == 3) {
                    if (dx < 0) TouchpadShortcut.NEXT_APP else TouchpadShortcut.PREVIOUS_APP
                } else {
                    if (dx < 0) TouchpadShortcut.NEXT_DESKTOP else TouchpadShortcut.PREVIOUS_DESKTOP
                }
            abs(dy) >= distance && abs(dy) > abs(dx) * 1.3f && points.all {
                (it.y - initial.getValue(it.id).y) * (if (dy < 0) -1 else 1) >= distance / 2
            } ->
                if (dy < 0) TouchpadShortcut.TASK_VIEW
                else if (ids.size == 4) TouchpadShortcut.SHOW_DESKTOP else null
            else -> null
        }
    }

    /** Freeze the full-cohort result so staggered lifts cannot change its centroid. */
    fun contactsLifted(remaining: List<MultiFingerState.Point>) {
        if (blocked) return
        if (remaining.size >= ids.size || remaining.any { it.id !in ids }) {
            cancel()
            return
        }
        draining = true
    }

    fun finish(): TouchpadShortcut? {
        val result = if (blocked) null else pending
        cancel()
        return result
    }

    fun cancel() {
        blocked = true
        draining = false
        ids = emptySet()
        initial = emptyMap()
        pending = null
    }
}
