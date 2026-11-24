package com.github.ifeel.keysjoy.input

/** Decide tap/double-click/drag before sending clicks to Windows Shell. */
class TapDragState {
    enum class Action { CLICK, DOWN, UP }
    var pendingSingle = false
        private set
    var secondPressed = false
        private set
    var dragging = false
        private set
    fun firstTap(): List<Action> {
        val old=confirmSingle()
        pendingSingle=true
        return old
    }
    fun confirmSingle(): List<Action> {
        if(!pendingSingle)return emptyList()
        pendingSingle=false
        return listOf(Action.CLICK)
    }
    fun secondDown() { pendingSingle=false;secondPressed=true;dragging=false }
    fun startDrag(): List<Action> {
        if(!secondPressed||dragging)return emptyList()
        dragging=true
        return listOf(Action.DOWN)
    }
    fun secondUp(): List<Action> {
        if(!secondPressed)return emptyList()
        val result=if(dragging)listOf(Action.UP) else listOf(Action.CLICK,Action.CLICK)
        secondPressed=false;dragging=false
        return result
    }
    fun cancel(): List<Action> {
        val result=if(dragging)listOf(Action.UP) else emptyList()
        pendingSingle=false;secondPressed=false;dragging=false
        return result
    }
}
