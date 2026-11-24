package com.github.ifeel.keysjoy

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.listeners.ViewListener
import com.github.ifeel.keysjoy.input.KeyboardModifiers
import com.github.ifeel.keysjoy.ui.ControlScreen
import android.view.ViewGroup
import android.widget.Button

/** Sends events only to this app's own view, without INJECT_EVENTS or Root.
 * Moves only: it never clicks a potentially unrelated Windows application.
 */
class UsbSmokeInstrumentation : Instrumentation() {
    private var homePreview=false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);homePreview=arguments?.getString("home_preview")=="true"; start() }

    private fun openConnected(): Activity {
        val activity = startActivitySync(Intent(targetContext, UsbTouchpadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(80) {
            var connected = false
            runOnMainSync {
                connected = activity.findViewById<TextView>(R.id.usb_status).text.contains("电脑已连接")
            }
            if (connected) return activity
            SystemClock.sleep(100)
        }
        error("USB did not connect within 8 seconds")
    }

    override fun onStart() {
        if(homePreview) {
            val result=Bundle()
            try {
                var home=startActivitySync(Intent(targetContext,SplashScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                for(wide in listOf(true,false)) {
                    val orientation=if(wide)android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
                    if(home.resources.configuration.orientation!=orientation)home=nextActivity(SplashScreenActivity::class.java.name){home.requestedOrientation=if(wide)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                    screenshot(if(wide)"home-landscape" else "home-portrait")
                }
                result.putString("result","Home previews captured")
                finish(Activity.RESULT_OK,result)
            } catch(e:Throwable){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result)}
            return
        }

        val result = Bundle()
        val uiPrefs=targetContext.getSharedPreferences("control_ui",android.content.Context.MODE_PRIVATE)
        val previousKeyboard=uiPrefs.getBoolean("keyboard",false)
        try {
            var activity = openConnected()
            activity = orient(activity, false)
            verifyKeyboardUi(activity)
            result.putString("keyboard_ui", "PASS: native IME commits Chinese/emoji, keeps composition local, and emits deletion")
            verifyPointerGestures(activity)
            result.putString("gesture_semantics", "PASS: move/hold never select, tap-then-slide drags, double tap clicks, wheel down/up directions")
            verifyDragPointerTransitions(activity)
            result.putString("drag_regression", "PASS: pointer reorder/lift has no jump, scroll or right-click during drag")
            val pad = activity.findViewById<View>(R.id.simple_trackpad_touch)
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float) {
                runOnMainSync {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, 800f, 0)
                    check(pad.dispatchTouchEvent(event))
                    event.recycle()
                }
            }
            touch(MotionEvent.ACTION_DOWN, 350f)
            repeat(15) { SystemClock.sleep(16); touch(MotionEvent.ACTION_MOVE, 350f + (it + 1) * 10) }
            touch(MotionEvent.ACTION_UP, 500f)
            SystemClock.sleep(400)
            result.putString("movement", "Own-view swipe sent while USB connected; verify Windows cursor separately")
            val monitor = ActivityMonitor(UsbTouchpadActivity::class.java.name, null, false)
            addMonitor(monitor)
            runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val landscape = waitForMonitorWithTimeout(monitor, 5000) ?: error("Landscape recreation timed out")
            removeMonitor(monitor)
            activity = landscape
            waitForIdleSync()
            SystemClock.sleep(700)
            runOnMainSync {
                val surface = activity.findViewById<View>(R.id.simple_trackpad_touch)
                val density = activity.resources.displayMetrics.density
                check(surface.width >= 160 * density && surface.height >= 120 * density) { "Landscape touch surface too small" }
            }
            val capture = uiAutomation.takeScreenshot()
            if (capture != null) {
                java.io.FileOutputStream(java.io.File(targetContext.filesDir, "mambapad-landscape.png")).use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                capture.recycle()
            }
            result.putString("landscape", "PASS: app-only rotation, readable controls and usable touch surface")
            runOnMainSync { activity.finish() }
            SystemClock.sleep(400)
            activity = openConnected()
            result.putString("lifecycle", "PASS: activity close and reopen reconnected")
            verifyLayoutsAndNavigation(activity)
            result.putString("layouts_navigation", "PASS: four layouts, USB/BT home buttons, and Bluetooth system Back")
            result.putString("result", "PASS")
            uiPrefs.edit().putBoolean("keyboard",previousKeyboard).commit()
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("result", "FAIL")
            result.putString("error", e.toString())
            uiPrefs.edit().putBoolean("keyboard",previousKeyboard).commit()
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun nextActivity(className:String, action:()->Unit):Activity {
        val monitor=ActivityMonitor(className,null,false)
        addMonitor(monitor)
        runOnMainSync{action()}
        val activity=waitForMonitorWithTimeout(monitor,7000)
        removeMonitor(monitor)
        check(activity!=null){"Activity transition timed out: $className"}
        waitForIdleSync();SystemClock.sleep(200)
        return activity!!
    }
    private fun orient(activity:Activity,wide:Boolean):Activity {
        val desired=if(wide)android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
        if(activity.resources.configuration.orientation==desired)return activity
        return nextActivity(UsbTouchpadActivity::class.java.name){activity.requestedOrientation=if(wide)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
    }
    private fun keyboard(activity:Activity,show:Boolean) {
        runOnMainSync {
            val root=activity.findViewById<View>(R.id.control_root)
            if((root.tag=="keyboard")!=show)activity.findViewById<View>(R.id.control_layout).performClick()
        }
        waitForIdleSync();SystemClock.sleep(120)
    }
    private fun screenshot(name:String) {
        waitForIdleSync();SystemClock.sleep(250)
        val bitmap=uiAutomation.takeScreenshot()?:return
        java.io.FileOutputStream(java.io.File(targetContext.filesDir,"$name.png")).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private fun verifyKeyboardUi(activity:Activity) {
        val edits=mutableListOf<Pair<Int,String>>()
        lateinit var editor:com.github.ifeel.keysjoy.ui.RemoteImeEditor
        lateinit var input:android.view.inputmethod.InputConnection
        runOnMainSync {
            val screen=ControlScreen(activity,false,true,KeyboardModifiers())
            screen.onText={count,text->edits.add(count to text)}
            editor=screen.editor!!
            input=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
            input.setComposingText("zhong",1)
        }
        waitForIdleSync();check(edits.isEmpty()){ "Composition leaked: $edits" }
        runOnMainSync { input.commitText("中文😀",1) }
        waitForIdleSync();check(edits==listOf(0 to "中文😀")){ "Commit failed: $edits" }
        runOnMainSync {input.deleteSurroundingTextInCodePoints(1,0)}
        waitForIdleSync();check(edits.last()==(1 to "")){ "Emoji deletion failed: $edits" }
        runOnMainSync {input.setComposingText("abc",1)}
        waitForIdleSync();check(edits.size==2)
        runOnMainSync {input.finishComposingText()}
        waitForIdleSync();check(edits.last()==(0 to "abc")){ "Finish composition failed: $edits" }
    }

    private fun verifySplitKeyboardUi(activity:Activity) {
        val keys=mutableListOf<Pair<Int,Int>>()
        runOnMainSync {
            val screen=ControlScreen(activity,false,true,KeyboardModifiers())
            screen.onKey={usage,mask->keys.add(usage to mask)}
            fun all(view:View):List<Button> = if(view is Button)listOf(view) else if(view is ViewGroup)(0 until view.childCount).flatMap{all(view.getChildAt(it))} else emptyList()
            fun click(label:String){all(screen.root).first{it.text.toString()==label}.performClick()}
            click("Alt");click("A");click("Win");click("R");click("B")
        }
        check(keys==listOf(4 to 4,21 to 8,5 to 0)){"Split keyboard modifiers: $keys"}
    }
    private fun verifyLayoutsAndNavigation(initial:Activity) {
        var a=orient(initial,false)
        keyboard(a,false);screenshot("control-portrait-touchpad")
        a=orient(a,true)
        verifySplitKeyboardUi(a)
        keyboard(a,false)
        runOnMainSync {
            val root=a.findViewById<View>(R.id.control_root);val pad=a.findViewById<View>(R.id.simple_trackpad_touch)
            check(pad.width>root.width*0.90f && pad.height>root.height*0.65f){"Landscape pure pad too small: ${pad.width}x${pad.height}, root ${root.width}x${root.height}"}
        }
        screenshot("control-landscape-touchpad")
        keyboard(a,true)
        runOnMainSync {
            val root=a.findViewById<View>(R.id.control_root);val pad=a.findViewById<View>(R.id.simple_trackpad_touch)
            check(pad.width in (root.width*0.40f).toInt()..(root.width*0.60f).toInt())
        }
        screenshot("control-landscape-keyboard")
        a=orient(a,false)
        runOnMainSync {
            val root=a.findViewById<View>(R.id.control_root);val pad=a.findViewById<View>(R.id.simple_trackpad_touch)
            val keys=a.findViewById<View>(R.id.control_keyboard)
            check(pad.height>root.height*0.30f && keys.height>0)
        }
        screenshot("control-portrait-keyboard")
        var home=nextActivity(SplashScreenActivity::class.java.name){a.findViewById<View>(R.id.control_back).performClick()}
        runOnMainSync{check(home.findViewById<View>(R.id.home_usb)!=null && home.findViewById<View>(R.id.home_bluetooth)!=null)}
        a=nextActivity(UsbTouchpadActivity::class.java.name){home.findViewById<View>(R.id.home_bluetooth).performClick()}
        screenshot("control-bluetooth-keyboard")
        home=nextActivity(SplashScreenActivity::class.java.name){a.findViewById<View>(R.id.control_back).performClick()}
        a=nextActivity(UsbTouchpadActivity::class.java.name){home.findViewById<View>(R.id.home_bluetooth).performClick()}
        runOnMainSync {a.findViewById<com.github.ifeel.keysjoy.ui.RemoteImeEditor>(R.id.control_ime)?.hideKeyboard()}
        SystemClock.sleep(500)
        home=nextActivity(SplashScreenActivity::class.java.name){
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK))
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK))
        }
        runOnMainSync{check(home.findViewById<View>(R.id.home_usb)!=null)}
    }

    private class Capture : MouseInputSender {
        val moves = mutableListOf<Pair<Int, Int>>()
        var clicks = 0
        val wheel = mutableListOf<Pair<Int,Int>>()
        var scrolls = 0
        var rightClicks = 0
        var down = 0
        var up = 0
        override fun sendMouseMove(dx: Int, dy: Int) { moves.add(dx to dy) }
        override fun sendScroll(vscroll: Int, hscroll: Int) { scrolls++;wheel.add(vscroll to hscroll) }
        override fun sendTestClick() {clicks++}
        override fun sendRightClick() { rightClicks++ }
        override fun sendLeftClickOn() { down++ }
        override fun sendLeftClickOff() { up++ }
        override fun sendDragMove(dx: Int, dy: Int) = sendMouseMove(dx, dy)
        override fun wake() {}
        override fun releaseAll() {}
    }

    private fun verifyPointerGestures(activity:Activity) {
        fun scenario(test:(ViewListener,Capture,(Int,Float,Float,Boolean)->Unit)->Unit) {
            val capture=Capture()
            lateinit var listener:ViewListener
            lateinit var view:View
            runOnMainSync {listener=ViewListener(capture).apply{mouseSpeed=1f;scrollSpeed=0.1f;rampEnabled=false};view=View(activity)}
            var downTime=SystemClock.uptimeMillis()
            val send={action:Int,x:Float,y:Float,wheelButton:Boolean->
                runOnMainSync {
                    if(action==MotionEvent.ACTION_DOWN)downTime=SystemClock.uptimeMillis()
                    val e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0)
                    if(wheelButton)listener.onWheelButtonTouch(e) else listener.onTouch(view,e)
                    e.recycle()
                }
            }
            try{test(listener,capture,send)}finally{runOnMainSync{listener.cancelAndRelease()}}
        }
        scenario{_,c,send->
            send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(700)
            check(c.down==0){"Holding a single touch started selection"}
            send(MotionEvent.ACTION_MOVE,200f,500f,false)
            send(MotionEvent.ACTION_UP,200f,500f,false)
            check(c.down==0&&c.up==0&&c.clicks==0&&c.moves.isNotEmpty()){ "Plain move became a drag" }
        }
        scenario{_,c,send->
            send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            check(c.clicks==0){"First tap must wait for drag classification"}
            SystemClock.sleep(80);send(MotionEvent.ACTION_DOWN,200f,400f,false)
            send(MotionEvent.ACTION_MOVE,230f,440f,false)
            check(c.clicks==0&&c.down==1&&c.up==0&&c.moves.sumOf{it.second}==40){"Second-tap slide did not drag"}
            send(MotionEvent.ACTION_CANCEL,230f,440f,false)
            check(c.up==1){"Cancelled drag did not release"}
        }
        scenario{_,c,send->
            send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            SystemClock.sleep(80);send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            check(c.clicks==2&&c.down==0&&c.up==0){"Quick double tap must emit two complete clicks"}
        }
        for(direct in listOf(true,false))scenario{listener,c,send->
            runOnMainSync{listener.setWheelMode(true)}
            send(MotionEvent.ACTION_DOWN,100f,200f,direct)
            send(MotionEvent.ACTION_MOVE,100f,300f,direct)
            send(MotionEvent.ACTION_UP,100f,300f,direct)
            check(c.wheel.sumOf{it.first}==-10){"Downward wheel gesture must scroll down: ${c.wheel}"}
            c.wheel.clear()
            send(MotionEvent.ACTION_DOWN,100f,300f,direct)
            send(MotionEvent.ACTION_MOVE,100f,200f,direct)
            send(MotionEvent.ACTION_UP,100f,200f,direct)
            check(c.wheel.sumOf{it.first}==10){"Upward wheel gesture must scroll up: ${c.wheel}"}
            check(c.down==0&&c.clicks==0&&c.moves.isEmpty()){ "Wheel gesture leaked mouse events" }
        }
    }

    private fun verifyDragPointerTransitions(activity: Activity) {
        val capture = Capture()
        lateinit var listener: ViewListener
        lateinit var view: View
        runOnMainSync {
            listener = ViewListener(capture).also { it.mouseSpeed = 1f; it.rampEnabled = false }
            view = View(activity)
        }
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, vararg points: Triple<Int, Float, Float>) {
            runOnMainSync {
                val properties = points.map { point -> MotionEvent.PointerProperties().apply { id = point.first; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords = points.map { point -> MotionEvent.PointerCoords().apply { x = point.second; y = point.third; pressure = 1f; size = 0.1f } }.toTypedArray()
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, points.size, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                listener.onTouch(view, event)
                event.recycle()
            }
        }
        try {
            send(MotionEvent.ACTION_DOWN, Triple(7, 400f, 900f))
            SystemClock.sleep(60)
            send(MotionEvent.ACTION_UP, Triple(7, 400f, 900f))
            SystemClock.sleep(80)
            send(MotionEvent.ACTION_DOWN, Triple(7, 400f, 900f))
            send(MotionEvent.ACTION_MOVE, Triple(7, 400f, 950f))
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Triple(7, 400f, 950f), Triple(19, 100f, 100f))
            send(MotionEvent.ACTION_MOVE, Triple(19, 100f, 80f), Triple(7, 400f, 960f))
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Triple(19, 100f, 80f), Triple(7, 400f, 960f))
            send(MotionEvent.ACTION_MOVE, Triple(19, 100f, 90f))
            send(MotionEvent.ACTION_UP, Triple(19, 100f, 90f))
            check(capture.down == 1 && capture.up == 1) { "Drag must press and release once: ${capture.down}/${capture.up}" }
            check(capture.moves.sumOf { it.second } == 70) { "Unexpected drag deltas: ${capture.moves}" }
            check(capture.moves.all { it.first == 0 && it.second in 0..50 }) { "Pointer jump: ${capture.moves}" }
            check(capture.scrolls == 0 && capture.rightClicks == 0) { "Unexpected scroll/right click during drag" }
        } finally { runOnMainSync { listener.cancelAndRelease() } }
    }
}
