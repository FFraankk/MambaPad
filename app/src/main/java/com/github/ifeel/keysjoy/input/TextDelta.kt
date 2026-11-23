package com.github.ifeel.keysjoy.input

/** Compare code points so an emoji is erased once, never as half a surrogate. */
data class TextDelta(val deleteBefore: Int, val insert: String) {
    companion object {
        fun between(before: String, after: String): TextDelta {
            val a=before.codePoints().toArray();val b=after.codePoints().toArray()
            var prefix=0
            while(prefix<a.size&&prefix<b.size&&a[prefix]==b[prefix])prefix++
            return TextDelta(a.size-prefix,String(b,prefix,b.size-prefix))
        }
    }
}

/** Standard US HID layout, used for shortcuts and Bluetooth's keyboard profile. */
object AsciiHid {
    fun key(ch: Char): Pair<Int,Int>? {
        if(ch in 'a'..'z')return ch.code-'a'.code+4 to 0
        if(ch in 'A'..'Z')return ch.code-'A'.code+4 to 2
        val plain="1234567890\n\u001b\b\t -=[]\\#;'`,./"
        val usages=intArrayOf(30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56)
        plain.indexOf(ch).takeIf{it>=0}?.let{return usages[it] to 0}
        val shifted="!@#$%^&*()_+{}|:\"~<>?"
        val codes=intArrayOf(30,31,32,33,34,35,36,37,38,39,45,46,47,48,49,51,52,53,54,55,56)
        shifted.indexOf(ch).takeIf{it>=0}?.let{return codes[it] to 2}
        return null
    }
}
