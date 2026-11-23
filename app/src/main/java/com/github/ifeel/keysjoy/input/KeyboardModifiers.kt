package com.github.ifeel.keysjoy.input

/** One-shot USB HID modifier mask shared by both halves of the on-screen keyboard. */
class KeyboardModifiers {
    var mask: Int = 0
        private set
    fun toggle(bit: Int) { require(bit in 1..128 && bit and (bit - 1) == 0); mask = mask xor bit }
    fun take(): Int = mask.also { mask = 0 }
    fun reset() { mask = 0 }
    companion object {
        // Existing KeyDef modifier IDs are enum values 1,2,3,4, not HID bits.
        fun fromLegacyId(id:Int):Int=when(id){1->1;2->2;3->4;4->8;else->0}
    }
}
