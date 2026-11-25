package com.github.ifeel.keysjoy.input

/** Gesture output shared by Bluetooth and USB; no platform transport types. */
interface MouseInputSender {
    fun sendMouseMove(dx: Int, dy: Int)
    fun sendScroll(vscroll: Int, hscroll: Int)
    /** Ctrl+wheel zoom; implementations without a keyboard channel must not send plain scroll. */
    fun sendZoom(steps: Int) {}
    fun sendShortcut(shortcut: TouchpadShortcut) {}
    fun sendTestClick()
    fun sendRightClick()
    fun sendLeftClickOn()
    fun sendLeftClickOff()
    fun sendDragMove(dx: Int, dy: Int)
    fun wake()
    fun releaseAll()
}
