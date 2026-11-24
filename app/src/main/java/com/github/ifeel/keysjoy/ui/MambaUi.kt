package com.github.ifeel.keysjoy.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.github.ifeel.keysjoy.R

/** MambaPad's original native UI. No web view, bitmap backgrounds or UI framework. */
object MambaUi {
    val ink = Color.rgb(71, 43, 28)
    val muted = Color.rgb(143, 115, 93)
    val orange = Color.rgb(218, 67, 32)
    val cream = Color.rgb(255, 248, 235)
    val line = Color.rgb(239, 222, 197)
    fun Context.dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    fun shape(color: Int, radius: Float = 18f, border: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (border != null) setStroke(1, border)
    }
    fun text(c: Context, value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(c).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }
    fun button(c: Context, value: String, primary: Boolean = false) = Button(c).apply {
        text = value; textSize = 14f; isAllCaps = false
        stateListAnimator = null
        elevation = 0f
        setTextColor(if (primary) Color.WHITE else ink)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = RippleDrawable(ColorStateList.valueOf(0x22DF5425), shape(if (primary) orange else Color.WHITE, c.dp(16).toFloat(), if (primary) null else line), null)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(c.dp(12), 0, c.dp(12), 0)
    }
    fun logo(c: Context, size: Int = 42) = ImageView(c).apply {
        setImageResource(R.mipmap.ic_launcher)
        layoutParams = LinearLayout.LayoutParams(c.dp(size), c.dp(size))
        contentDescription = "MambaPad"
    }
    private fun iconButton(c: Context, icon: Int, label: String) = ImageButton(c).apply {
        setImageResource(icon); contentDescription = label
        imageTintList = ColorStateList.valueOf(muted)
        background = RippleDrawable(ColorStateList.valueOf(0x22DF5425), shape(Color.WHITE, c.dp(14).toFloat()), null)
        setPadding(c.dp(12), c.dp(12), c.dp(12), c.dp(12))
    }

    fun home(activity: Activity, usb: () -> Unit, bluetooth: () -> Unit) {
        with(activity) {
            val wide = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val body = LinearLayout(activity).apply {
                orientation = if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(28), dp(if(wide)16 else 32), dp(28), dp(if(wide)16 else 28))
                setBackgroundColor(cream)
            }
            val scroll = ScrollView(activity).apply { isFillViewport = true; setBackgroundColor(cream); addView(body) }
            val intro=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            val choices=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
            if(wide) {
                body.addView(intro,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(28)})
                body.addView(choices,LinearLayout.LayoutParams(0,-2,1f))
                val brand=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
                brand.addView(logo(activity,44))
                brand.addView(text(activity,"MambaPad",26f,ink,true).apply{setPadding(dp(12),0,0,0)})
                intro.addView(brand)
            } else {
                body.addView(intro,LinearLayout.LayoutParams(-1,-2))
                body.addView(choices,LinearLayout.LayoutParams(-1,-2))
                intro.addView(logo(activity,62))
                intro.addView(text(activity,"MambaPad",32f,ink,true).apply{setPadding(0,dp(24),0,0)})
            }
            intro.addView(text(activity,"指尖一划，\n轻松掌控。",if(wide)28f else 34f,ink,true).apply {
                setLineSpacing(dp(if(wide)4 else 8).toFloat(),1f)
                setPadding(0,dp(if(wide)18 else 26),0,dp(14))
            })
            intro.addView(text(activity,"把手机变成你的电脑触控板",if(wide)13f else 15f,muted))
            if(!wide)intro.addView(View(activity),LinearLayout.LayoutParams(1,dp(36)))
            fun card(id:Int,title:String,subtitle:String,primary:Boolean,click:()->Unit) {
                val box=LinearLayout(activity).apply {
                    this.id=id;orientation=LinearLayout.VERTICAL
                    setPadding(dp(22),dp(if(wide)17 else 22),dp(22),dp(if(wide)17 else 22))
                    background=RippleDrawable(ColorStateList.valueOf(0x22DF5425),shape(if(primary)orange else Color.WHITE,dp(22).toFloat(),if(primary)null else line),null)
                    isClickable=true;isFocusable=true;contentDescription="$title，$subtitle"
                    setOnClickListener{click()}
                }
                box.addView(text(activity,title,19f,if(primary)Color.WHITE else ink,true))
                box.addView(text(activity,subtitle,13f,if(primary)0xFFFFD9BE.toInt() else muted).apply{setPadding(0,dp(9),0,0)})
                choices.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})
            }
            card(R.id.home_usb,"USB 有线   →","插线即连 · 稳定低延迟",true,usb)
            card(R.id.home_bluetooth,"蓝牙无线   →","摆脱线缆 · 轻松切换",false,bluetooth)
            choices.addView(text(activity,"无需账号  /  无需定位",12f,muted).apply{setPadding(0,dp(if(wide)0 else 18),0,0)})
            setContentView(scroll)
        }
    }
}

class TouchpadSurface(context: Context) : View(context) {
    var connected = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fingers = emptyList<Pair<Float, Float>>()
    private val density = resources.displayMetrics.density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24*density, 24*density, paint)
        paint.color = 0xFFEADCC8.toInt()
        var x = 20*density
        while (x < width) {
            var y = 20*density
            while (y < height) { canvas.drawCircle(x, y, 0.7f*density, paint); y += 24*density }
            x += 24*density
        }
        paint.color = MambaUi.muted; paint.textSize = 11*density
        canvas.drawText("触控区", 20*density, 30*density, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = 0xFF8C6D55.toInt(); paint.textSize = 16*density
        if (height > 120*density) {
            canvas.drawText(if (connected) "指尖一划，即刻掌控" else "等待你的电脑", width/2f, height/2f, paint)
            paint.textSize = 11*density
            canvas.drawText("单指移动  ·  双指滚动  ·  轻点后再按住拖动", width/2f, height/2f+26*density, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        for ((fx, fy) in fingers) {
            paint.color = 0x22E76B24
            canvas.drawCircle(fx, fy, 23*density, paint)
            paint.color = 0x99E76B24.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            canvas.drawCircle(fx, fy, 23*density, paint); paint.style = Paint.Style.FILL
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        fingers = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) emptyList()
            else (0 until event.pointerCount).filter { event.actionMasked != MotionEvent.ACTION_POINTER_UP || it != event.actionIndex }.map { event.getX(it) to event.getY(it) }
        invalidate()
        return super.dispatchTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
