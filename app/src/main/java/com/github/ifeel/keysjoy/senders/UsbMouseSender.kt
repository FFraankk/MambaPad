package com.github.ifeel.keysjoy.senders

import com.github.ifeel.keysjoy.input.InputTransport
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.input.RemoteInputEvent

class UsbMouseSender(private val transport: InputTransport) : MouseInputSender {
    override fun sendMouseMove(dx: Int, dy: Int) {
        var x = dx; var y = dy
        while (x != 0 || y != 0) {
            val a = x.coerceIn(-32767, 32767); val b = y.coerceIn(-32767, 32767)
            transport.send(RemoteInputEvent.Move(a, b)); x -= a; y -= b
        }
    }
    override fun sendScroll(vscroll: Int, hscroll: Int) {
        // ViewListener produces logical wheel ticks; ADB protocol uses Windows units.
        var v = vscroll.toLong() * 120; var h = hscroll.toLong() * 120
        while (v != 0L || h != 0L) {
            val a = v.coerceIn(-32767, 32767).toInt(); val b = h.coerceIn(-32767, 32767).toInt()
            transport.send(RemoteInputEvent.Scroll(a, b)); v -= a; h -= b
        }
    }
    fun button(button: String, down: Boolean) { transport.send(RemoteInputEvent.Button(button, down)) }
    override fun sendTestClick() { button("left", true); button("left", false) }
    override fun sendRightClick() { button("right", true); button("right", false) }
    override fun sendLeftClickOn() = button("left", true)
    override fun sendLeftClickOff() = button("left", false)
    override fun sendDragMove(dx: Int, dy: Int) { sendLeftClickOn(); sendMouseMove(dx, dy) }
    override fun wake() { /* Bluetooth wake reports must not reset USB button state. */ }
    override fun releaseAll() { transport.send(RemoteInputEvent.ReleaseAll) }
}
