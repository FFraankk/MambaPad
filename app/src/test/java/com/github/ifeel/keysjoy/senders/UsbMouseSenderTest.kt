package com.github.ifeel.keysjoy.senders

import com.github.ifeel.keysjoy.input.InputTransport
import com.github.ifeel.keysjoy.input.RemoteInputEvent
import org.junit.Assert.*
import org.junit.Test

class UsbMouseSenderTest {
    private class Capture : InputTransport {
        override val isConnected = true
        val events = mutableListOf<RemoteInputEvent>()
        override fun send(event: RemoteInputEvent) { events.add(event) }
        override fun close() {}
    }
    @Test fun dragMaintainsOrderAndIndependentButtonReleases() {
        val t = Capture(); val s = UsbMouseSender(t)
        s.sendLeftClickOn(); s.sendMouseMove(12, -3); s.button("right", true)
        s.sendLeftClickOff(); s.button("right", false)
        assertEquals(listOf(
            RemoteInputEvent.Button("left", true), RemoteInputEvent.Move(12, -3),
            RemoteInputEvent.Button("right", true), RemoteInputEvent.Button("left", false),
            RemoteInputEvent.Button("right", false)
        ), t.events)
    }
    @Test fun fastMovementPreservesTotalDistanceWithinProtocolRange() {
        val t = Capture(); UsbMouseSender(t).sendMouseMove(100000, -80000)
        val moves = t.events.filterIsInstance<RemoteInputEvent.Move>()
        assertEquals(100000, moves.sumOf { it.dx }); assertEquals(-80000, moves.sumOf { it.dy })
        assertTrue(moves.all { it.dx in -32767..32767 && it.dy in -32767..32767 })
    }
    @Test fun scrollingConvertsTicksToWindowsUnitsWithoutByteWrap() {
        val t = Capture(); UsbMouseSender(t).sendScroll(300, -400)
        val events = t.events.filterIsInstance<RemoteInputEvent.Scroll>()
        assertEquals(36000, events.sumOf { it.vertical }); assertEquals(-48000, events.sumOf { it.horizontal })
        assertTrue(events.all { it.vertical in -32767..32767 && it.horizontal in -32767..32767 })
    }
    @Test fun bluetoothWakeDoesNotClearUsbDrag() {
        val t = Capture(); val s = UsbMouseSender(t)
        s.sendLeftClickOn(); s.wake(); s.sendMouseMove(1, 1); s.releaseAll()
        assertEquals(3, t.events.size)
        assertEquals(RemoteInputEvent.ReleaseAll, t.events.last())
    }
}
