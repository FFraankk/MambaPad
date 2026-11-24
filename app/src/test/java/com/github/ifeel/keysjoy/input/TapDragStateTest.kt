package com.github.ifeel.keysjoy.input
import org.junit.Assert.*
import org.junit.Test
import com.github.ifeel.keysjoy.input.TapDragState.Action.*
class TapDragStateTest {
 @Test fun singleClickWaitsForClassification() {
  val s=TapDragState();assertTrue(s.firstTap().isEmpty())
  assertEquals(listOf(CLICK),s.confirmSingle());assertTrue(s.confirmSingle().isEmpty())
 }
 @Test fun desktopDragContainsNoPrecedingClick() {
  val s=TapDragState();val out=s.firstTap().toMutableList();s.secondDown()
  out.addAll(s.startDrag());out.addAll(s.secondUp());assertEquals(listOf(DOWN,UP),out)
 }
 @Test fun stationarySecondTapIsStillDoubleClick() {
  val s=TapDragState();s.firstTap();s.secondDown()
  assertEquals(listOf(CLICK,CLICK),s.secondUp());assertTrue(s.secondUp().isEmpty())
 }
 @Test fun staleSingleConfirmationCannotClickDuringDrag() {
  val s=TapDragState();s.firstTap();s.secondDown();s.startDrag()
  assertTrue(s.confirmSingle().isEmpty());assertTrue(s.startDrag().isEmpty())
 }
 @Test fun cancellationReleasesOnlyAnActualDrag() {
  val s=TapDragState();s.firstTap();s.secondDown();assertTrue(s.cancel().isEmpty())
  s.firstTap();s.secondDown();s.startDrag();assertEquals(listOf(UP),s.cancel())
  assertTrue(s.cancel().isEmpty());assertTrue(s.confirmSingle().isEmpty())
 }
}
