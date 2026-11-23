package com.github.ifeel.keysjoy.input

sealed interface RemoteInputEvent {
    data class Move(val dx: Int, val dy: Int) : RemoteInputEvent
    data class Button(val button: String, val down: Boolean) : RemoteInputEvent
    /** Windows wheel units (120 = one notch), not HID wheel ticks. */
    data class Scroll(val vertical: Int, val horizontal: Int) : RemoteInputEvent
    data class Key(val usage: Int, val down: Boolean, val modifiers: Int = 0) : RemoteInputEvent
    data class Text(val text: String) : RemoteInputEvent
    data object ReleaseAll : RemoteInputEvent
}

interface InputTransport {
    val isConnected: Boolean
    val supportsKeyboard: Boolean get() = false
    fun send(event: RemoteInputEvent)
    fun close()
}
