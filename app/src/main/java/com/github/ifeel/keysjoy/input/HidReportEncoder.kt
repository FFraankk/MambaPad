package com.github.ifeel.keysjoy.input

object HidReportEncoder {
    fun mouse(buttons:Int,dx:Int,dy:Int,vertical:Int,horizontal:Int):ByteArray {
        require(buttons in 0..7 && dx in -2047..2047 && dy in -2047..2047 && vertical in -127..127 && horizontal in -127..127)
        return byteArrayOf(buttons.toByte(),dx.toByte(),(dx shr 8).toByte(),dy.toByte(),(dy shr 8).toByte(),vertical.toByte(),horizontal.toByte())
    }
    fun keyboard(usage:Int,modifiers:Int):ByteArray {
        require(usage in 0..255 && modifiers in 0..255)
        return byteArrayOf(modifiers.toByte(),0,usage.toByte())
    }
}
