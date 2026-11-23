package com.github.ifeel.keysjoy.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** Real system IME editor. Composing text stays local until the IME commits it. */
class RemoteImeEditor(context: Context) : EditText(context) {
    var onEdit: (Int, String) -> Unit = { _, _ -> }
    private var sent = ""
    private var internal = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val flush = Runnable { flushCommitted() }
    init {
        hint = "点击使用手机输入法"
        textSize = 13f
        maxLines = 1
        isSaveEnabled = false
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setSelectAllOnFocus(false)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!internal) { main.removeCallbacks(flush); main.post(flush) }
            }
        })
    }
    override fun onCreateInputConnection(info: EditorInfo): android.view.inputmethod.InputConnection? {
        val base = super.onCreateInputConnection(info) ?: return null
        return object : android.view.inputmethod.InputConnectionWrapper(base, false) {
            override fun finishComposingText(): Boolean {
                val result=super.finishComposingText(); main.removeCallbacks(flush); main.post(flush); return result
            }
            override fun commitText(value: CharSequence?, cursor: Int): Boolean {
                val result=super.commitText(value,cursor); main.removeCallbacks(flush); main.post(flush); return result
            }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                if (text.isEmpty() && before>0) { onEdit(before.coerceAtMost(64), ""); return true }
                return super.deleteSurroundingText(before,after)
            }
            override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean {
                if (text.isEmpty() && before>0) { onEdit(before.coerceAtMost(64), ""); return true }
                return super.deleteSurroundingTextInCodePoints(before,after)
            }
            override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                if (text.isEmpty() && event.keyCode==android.view.KeyEvent.KEYCODE_DEL) {
                    if(event.action==android.view.KeyEvent.ACTION_DOWN)onEdit(1, "")
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }
    private fun flushCommitted() {
        val value=text ?: return
        if(BaseInputConnection.getComposingSpanStart(value)>=0)return
        val current=value.toString()
        val delta=com.github.ifeel.keysjoy.input.TextDelta.between(sent,current)
        sent=current
        if(delta.deleteBefore!=0 || delta.insert.isNotEmpty())onEdit(delta.deleteBefore,delta.insert)
        // Keep short local context for IME correction, never persist typed text.
        if(current.length>2048)resetContext()
    }
    fun resetContext() {
        main.removeCallbacks(flush);internal=true;setText("");sent="";internal=false
    }
    fun showKeyboard() {
        requestFocus()
        post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this,InputMethodManager.SHOW_IMPLICIT) }
    }
    fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken,0)
    }
    override fun onDetachedFromWindow(){main.removeCallbacks(flush);super.onDetachedFromWindow()}
}
