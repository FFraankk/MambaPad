package com.github.ifeel.keysjoy.senders

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.os.Handler
import android.os.Looper
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.input.HidReportEncoder
import com.github.ifeel.keysjoy.input.TouchpadShortcut
import com.github.ifeel.keysjoy.reports.KeyboardReport
import com.github.ifeel.keysjoy.reports.ScrollableTrackpadMouseReport

/** Main-thread state owner shared by all controls in the Bluetooth page. */
class BluetoothInputSender(private val hid: BluetoothHidDevice, private val host: BluetoothDevice) : MouseInputSender {
    private var buttons = 0
    private var closed = false
    private val handler = Handler(Looper.getMainLooper())
    private data class KeyboardAction(val usage: Int, val modifiers: Int, val zoom: Int = 0)
    private val keys = java.util.ArrayDeque<KeyboardAction>()
    private var keyBusy = false
    private fun report(id: Int, bytes: ByteArray) {
        // Permission can be revoked while an existing HID session is active.
        try { hid.sendReport(host, id, bytes) } catch (_: SecurityException) {
            closed = true
            handler.removeCallbacksAndMessages(null)
            keys.clear()
        }
    }
    private fun mouse(dx: Int = 0, dy: Int = 0, v: Int = 0, h: Int = 0) {
        if (closed) return
        report(ScrollableTrackpadMouseReport.ID, HidReportEncoder.mouse(buttons,dx,dy,v,h))
    }
    fun button(name: String, down: Boolean) {
        val mask = when(name) { "left" -> 1; "right" -> 2; else -> 4 }
        buttons = if (down) buttons or mask else buttons and mask.inv()
        mouse()
    }
    override fun sendMouseMove(dx: Int, dy: Int) {
        var x = dx; var y = dy
        while (x != 0 || y != 0) {
            val a = x.coerceIn(-2047, 2047); val b = y.coerceIn(-2047, 2047)
            mouse(a,b); x -= a; y -= b
        }
    }
    override fun sendScroll(vscroll: Int, hscroll: Int) {
        var v = vscroll; var h = hscroll
        while (v != 0 || h != 0) {
            val a = v.coerceIn(-127, 127); val b = h.coerceIn(-127, 127)
            mouse(v=a,h=b); v -= a; h -= b
        }
    }
    override fun sendTestClick() { button("left",true); button("left",false) }
    override fun sendRightClick() { button("right",true); button("right",false) }
    override fun sendLeftClickOn() = button("left",true)
    override fun sendLeftClickOff() = button("left",false)
    override fun sendDragMove(dx: Int, dy: Int) { button("left",true); sendMouseMove(dx,dy) }
    override fun wake() { mouse() } // Preserve held buttons while waking the HID link.
    fun key(usage: Int, modifiers: Int) {
        if (closed || keys.size >= 128) return
        keys.add(KeyboardAction(usage, modifiers))
        nextKey()
    }
    override fun sendZoom(steps: Int) {
        if (closed || steps == 0 || keys.size >= 128) return
        // Serialize zoom with keyboard presses so their delayed UP cannot clear Ctrl mid-zoom.
        keys.add(KeyboardAction(0, 1, steps))
        nextKey()
    }
    override fun sendShortcut(shortcut: TouchpadShortcut) = key(shortcut.usage, shortcut.modifiers)
    private fun nextKey() {
        if (closed || keyBusy || keys.isEmpty()) return
        keyBusy = true
        val (usage, modifiers, zoom) = keys.removeFirst()
        report(KeyboardReport.ID, HidReportEncoder.keyboard(usage,modifiers))
        if (zoom != 0) {
            try { sendScroll(zoom, 0) } finally {
                if (!closed) report(KeyboardReport.ID, byteArrayOf(0,0,0))
                keyBusy = false
            }
            nextKey()
            return
        }
        handler.postDelayed({
            if (!closed) report(KeyboardReport.ID,byteArrayOf(0,0,0))
            keyBusy = false
            nextKey()
        }, 35)
    }
    override fun releaseAll() {
        handler.removeCallbacksAndMessages(null); keys.clear(); keyBusy = false
        buttons = 0
        mouse()
        if (!closed) report(KeyboardReport.ID, byteArrayOf(0,0,0))
    }
    fun close() { releaseAll(); closed = true }
}
