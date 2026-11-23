package com.github.ifeel.keysjoy.input

/** Tracks pointer IDs, never indexes or the multi-touch centroid. */
class DragPointerTracker {
    data class Point(val id: Int, val x: Float, val y: Float)
    data class Delta(val x: Float, val y: Float)
    private var previous: Point? = null

    fun clear() { previous = null }

    /** Pointer down/up changes establish a baseline, never a movement. */
    fun rebase(points: List<Point>) {
        previous = points.firstOrNull { it.id == previous?.id } ?: points.firstOrNull()
    }

    fun move(points: List<Point>): Delta? {
        val old = previous
        val current = points.firstOrNull { it.id == old?.id }
        if (old == null || current == null) {
            rebase(points)
            return null
        }
        previous = current
        return Delta(current.x - old.x, current.y - old.y)
    }
}
