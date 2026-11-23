package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test

class KeyboardModifiersTest {
    @Test fun legacyEnumIdsBecomeActualHidModifierBits() {
        assertEquals(listOf(1,2,4,8),(1..4).map{KeyboardModifiers.fromLegacyId(it)})
    }
    @Test fun modifiersAreOneShotAndDoNotLeakToNextKey() {
        val m=KeyboardModifiers();m.toggle(1);m.toggle(2)
        assertEquals(3,m.take());assertEquals(0,m.take())
    }
    @Test fun secondTapCancelsAndExitResetsModifiers() {
        val m=KeyboardModifiers();m.toggle(1);m.toggle(1);assertEquals(0,m.mask)
        m.toggle(8);m.reset();assertEquals(0,m.mask)
    }
}
