package com.github.ifeel.keysjoy.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.github.ifeel.keysjoy.R
import com.github.ifeel.keysjoy.input.KeyboardModifiers
import com.github.ifeel.keysjoy.ui.MambaUi.dp

/** One view tree for USB and Bluetooth. Connection controls are the only difference. */
class ControlScreen(val activity: Activity, val bluetooth: Boolean, val keyboard: Boolean, private val modifiers: KeyboardModifiers) {
    val root = LinearLayout(activity)
    val back = MambaUi.button(activity, "‹")
    val layout = MambaUi.button(activity, if (keyboard) "纯触控" else "键盘")
    val rotate = MambaUi.button(activity, "横屏")
    val settings = MambaUi.button(activity, "设置")
    val connect = MambaUi.button(activity, if (bluetooth) "连接设备" else "重新连接")
    val status = MambaUi.text(activity, "未连接", 11f, MambaUi.muted)
    val pad = TouchpadSurface(activity)
    val left = MambaUi.button(activity, "左键")
    val wheel = MambaUi.button(activity, "滚轮")
    val right = MambaUi.button(activity, "右键")
    var onKey: (Int, Int) -> Unit = { _, _ -> }
    private val modifierButtons = mutableListOf<Pair<Button, Int>>()
    private val letterButtons = mutableListOf<Pair<Button, String>>()
    var editor: RemoteImeEditor? = null
        private set
    var onText: (Int, String) -> Unit = { _, _ -> }
    private var symbols = false

    init {
        with(activity) {
            val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            root.orientation = LinearLayout.VERTICAL
            root.id = R.id.control_root
            root.tag = if(keyboard) "keyboard" else "touchpad"
            root.setBackgroundColor(MambaUi.cream)
            root.setPadding(dp(8), dp(3), dp(8), dp(6))
            val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            back.contentDescription = "返回主页"; back.id = R.id.control_back
            header.addView(back, LinearLayout.LayoutParams(dp(38), dp(40)))
            val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),0,0,0) }
            titleBox.addView(MambaUi.text(activity, if (bluetooth) "蓝牙无线" else "USB 有线", 16f, MambaUi.ink, true))
            status.id = R.id.usb_status
            titleBox.addView(status.apply { setPadding(0,dp(2),0,0) })
            header.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f))
            for ((button, width) in listOf(layout to 66, rotate to 50, settings to 50)) {
                button.textSize = 12f; button.setPadding(0,0,0,0)
                header.addView(button, LinearLayout.LayoutParams(dp(width),dp(38)).apply { marginStart=dp(5) })
            }
            layout.id = R.id.control_layout
            rotate.id = R.id.control_rotate
            rotate.text = if (wide) "竖屏" else "横屏"
            root.addView(header, LinearLayout.LayoutParams(-1,dp(46)))
            // The connection action lives in the settings dialog in landscape so
            // it cannot steal a permanent column from the touch surface.
            if (!wide) {
                val connectionRow = LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,0,dp(5)) }
                connectionRow.addView(MambaUi.text(activity, if (bluetooth) "配对电脑后开始操作" else "插线后允许 USB 调试", 11f, MambaUi.muted),LinearLayout.LayoutParams(0,-1,1f))
                connect.textSize=11f
                connectionRow.addView(connect,LinearLayout.LayoutParams(dp(92),dp(32)))
                root.addView(connectionRow,LinearLayout.LayoutParams(-1,dp(39)))
            }
            pad.id=R.id.simple_trackpad_touch
            pad.contentDescription="触控区：单指移动、轻点点击、双指滚动、长按拖动"
            left.id=R.id.btn_mouse_left; wheel.id=R.id.btn_mouse_middle; right.id=R.id.btn_mouse_right
            val touchBlock=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            touchBlock.addView(pad,LinearLayout.LayoutParams(-1,0,1f))
            val mouseRow=LinearLayout(activity).apply { setPadding(0,dp(5),0,0) }
            for ((index,b) in listOf(left,wheel,right).withIndex()) {
                b.textSize=13f
                mouseRow.addView(b,LinearLayout.LayoutParams(0,dp(if(wide) 38 else 44),1f).apply {if(index<2)marginEnd=dp(6)})
            }
            touchBlock.addView(mouseRow)
            if (wide && keyboard) {
                val body=LinearLayout(activity)
                body.addView(keyPanel(KeyLayouts.LEFT_PANEL.map { row -> row.filterNot { it.isSpecial }.map { uiKey(it) } }),LinearLayout.LayoutParams(0,-1,0.25f))
                body.addView(touchBlock,LinearLayout.LayoutParams(0,-1,0.50f).apply {marginStart=dp(6);marginEnd=dp(6)})
                body.addView(keyPanel(KeyLayouts.RIGHT_PANEL.map { row -> row.filterNot { it.isSpecial }.map { uiKey(it) } }),LinearLayout.LayoutParams(0,-1,0.25f))
                root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
            } else {
                root.addView(touchBlock,LinearLayout.LayoutParams(-1,0,1f))
                if (keyboard) {
                    val tools=HorizontalScrollView(activity).apply { id=R.id.control_keyboard;isHorizontalScrollBarEnabled=false }
                    val keys=listOf(UiKey("Ctrl",modifier=1),UiKey("Alt",modifier=4),UiKey("Win",modifier=8),UiKey("Shift",modifier=2),UiKey("Tab",43),UiKey("Esc",41),UiKey("←",80),UiKey("↑",82),UiKey("↓",81),UiKey("→",79),UiKey("Del",76))
                    val row=keyPanel(listOf(keys),fixedKeyWidth=56)
                    tools.addView(row,android.widget.FrameLayout.LayoutParams(-2,-1))
                    root.addView(tools,LinearLayout.LayoutParams(-1,dp(42)))
                    val input=RemoteImeEditor(activity).apply {
                        id=R.id.control_ime
                        setPadding(dp(10),0,dp(10),0)
                        background=MambaUi.shape(Color.WHITE,dp(7).toFloat(),MambaUi.line)
                        onEdit={count,text->onText(count,text)}
                    }
                    editor=input
                    val inputRow=LinearLayout(activity)
                    inputRow.addView(input,LinearLayout.LayoutParams(0,dp(36),1f))
                    inputRow.addView(MambaUi.button(activity,"收起").apply {
                        textSize=11f;setPadding(0,0,0,0);setOnClickListener{input.hideKeyboard()}
                    },LinearLayout.LayoutParams(dp(52),dp(36)))
                    root.addView(inputRow,LinearLayout.LayoutParams(-1,dp(36)))

                }
            }
        }
    }

    fun connection(connected: Boolean, detail: String = "") {
        pad.connected=connected
        status.text=if(connected) "电脑已连接" else detail.ifBlank { "等待连接" }
        status.setTextColor(if(connected)Color.rgb(62,117,83) else MambaUi.muted)
    }
    fun resetModifiers() { modifiers.reset(); updateModifierButtons() }
    private fun updateModifierButtons() {
        for ((button,bit) in modifierButtons) button.isSelected=modifiers.mask and bit != 0
        for ((button,bit) in modifierButtons) button.backgroundTintList=android.content.res.ColorStateList.valueOf(if(modifiers.mask and bit != 0)0xFFFFD997.toInt() else Color.WHITE)
        for ((button,label) in letterButtons) button.text=if(modifiers.mask and 2 != 0)label.uppercase() else label
    }
    private data class UiKey(val label:String,val usage:Int=0,val modifier:Int=0,val extra:Int=0,val span:Int=1,val symbols:Boolean=false)
    private fun uiKey(k:KeyDef):UiKey {
        val bit=if(k.isModifier&&k.label!="Caps")KeyboardModifiers.fromLegacyId(k.modifierId) else 0
        val label=if(bit!=0)when(bit){1->"Ctrl";2->"Shift";4->"Alt";8->"Win";else->k.label} else k.label
        return UiKey(label,k.hidCode,bit,span=k.colSpan)
    }
    private fun keyPanel(rows:List<List<UiKey>>,symbolAction:(()->Unit)?=null,fixedKeyWidth:Int=0):LinearLayout {
        val c=activity
        return LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL
            rows.forEach { row ->
                val line=LinearLayout(c)
                row.forEach { key ->
                    val b=MambaUi.button(c,key.label).apply {
                        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22DF5425),MambaUi.shape(Color.WHITE,c.dp(7).toFloat(),MambaUi.line),null)
                        textSize=if(c.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)10f else 12f
                        setPadding(0,0,0,0)
                        isSingleLine=true
                        contentDescription=key.label
                        isFocusable=false
                        setOnClickListener {
                            when {
                                key.symbols -> symbolAction?.invoke()
                                key.modifier!=0 -> {modifiers.toggle(key.modifier);updateModifierButtons()}
                                else -> {onKey(key.usage,modifiers.take() or key.extra);updateModifierButtons()}
                            }
                        }
                    }
                    if(key.modifier!=0)modifierButtons.add(b to key.modifier)
                    if(key.label.length==1&&key.label[0] in 'a'..'z')letterButtons.add(b to key.label)
                    line.addView(b,LinearLayout.LayoutParams(if(fixedKeyWidth>0)c.dp(fixedKeyWidth) else 0,-1,if(fixedKeyWidth>0)0f else key.span.toFloat()).apply {setMargins(c.dp(1),c.dp(1),c.dp(1),c.dp(1))})
                }
                addView(line,LinearLayout.LayoutParams(if(fixedKeyWidth>0)-2 else -1,0,1f))
            }
            updateModifierButtons()
        }
    }
}
