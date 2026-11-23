package com.github.ifeel.keysjoy.input

import org.junit.Assert.*
import org.junit.Test

class HidReportEncoderTest {
    @Test fun negativeMovementUsesLittleEndianWithoutClearingHeldButtons() {
        assertArrayEquals(byteArrayOf(3,0xfe.toByte(),0xff.toByte(),0,1,0xff.toByte(),2),HidReportEncoder.mouse(3,-2,256,-1,2))
    }
    @Test fun keyboardPreservesModifierMaskAndCanReleaseAll() {
        assertArrayEquals(byteArrayOf(3,0,6),HidReportEncoder.keyboard(6,3))
        assertArrayEquals(byteArrayOf(0,0,0),HidReportEncoder.keyboard(0,0))
    }
}
