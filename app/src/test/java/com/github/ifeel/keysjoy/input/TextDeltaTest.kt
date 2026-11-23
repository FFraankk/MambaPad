package com.github.ifeel.keysjoy.input
import org.junit.Assert.*
import org.junit.Test
class TextDeltaTest {
 @Test fun unicodeDeletionAndCorrection() {
  assertEquals(TextDelta(1,""),TextDelta.between("中文😀","中文"))
  assertEquals(TextDelta(3,"hello"),TextDelta.between("helol","hehello"))
  assertEquals(TextDelta(0,"文"),TextDelta.between("中","中文"))
 }
 @Test fun shortcutsAndBluetoothLayout() {
  assertEquals(6 to 0,AsciiHid.key('c'))
  assertEquals(31 to 2,AsciiHid.key('@'))
  assertEquals(49 to 0,AsciiHid.key('\\'))
  assertEquals(40 to 0,AsciiHid.key('\n'))
  assertNull(AsciiHid.key('中'))
 }
}
