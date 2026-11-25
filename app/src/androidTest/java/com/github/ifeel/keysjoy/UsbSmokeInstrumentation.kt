package com.github.ifeel.keysjoy

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.listeners.ViewListener
import com.github.ifeel.keysjoy.input.KeyboardModifiers
import com.github.ifeel.keysjoy.input.TouchpadShortcut
import com.github.ifeel.keysjoy.ui.ControlScreen
import android.view.ViewGroup
import android.widget.Button

/** Sends events only to this app's own view, without INJECT_EVENTS or Root.
 * edge_motion_only uses a fake sender. Opt-in edge_usb_case sends real mouse input
 * and requires the host to focus and guard a disposable selection-test window first.
 */
class UsbSmokeInstrumentation : Instrumentation() {
    private var feedbackPreview=false
    private var homePreview=false
    private var landscapeImeOnly=false
    private var edgeMotionOnly=false
    private var multiFingerOnly=false
    private var shortcutsOnly=false
    private var edgeUsbCase: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        shortcutsOnly=arguments?.getString("shortcuts_only")=="true"
        multiFingerOnly=arguments?.getString("multi_finger_only")=="true"
        edgeUsbCase=arguments?.getString("shortcut_usb_case")?:arguments?.getString("edge_usb_case")
        edgeMotionOnly=arguments?.getString("edge_motion_only")=="true"
        landscapeImeOnly=arguments?.getString("landscape_ime_only")=="true"
        feedbackPreview=arguments?.getString("feedback_preview")=="true"
        homePreview=arguments?.getString("home_preview")=="true"
        start()
    }

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
        edgeUsbCase?.let { scenario ->
            val result = Bundle()
            var activity: Activity? = null
            try {
                check(scenario in listOf("ordinary", "drag", "scroll", "zoom", "right_tap", "desktop_button",
                    "three_left", "three_right", "three_up", "three_down", "four_left", "four_right", "four_up", "four_down"))
                activity = openConnected()
                val pad = activity.findViewById<View>(R.id.simple_trackpad_touch)
                val density = activity.resources.displayMetrics.density
                var startX = 0f
                var endX = 0f
                var y = 0f
                runOnMainSync {
                    check(pad.width > 120 * density && pad.height > 80 * density)
                    startX = pad.width - 60 * density
                    endX = pad.width - 4 * density
                    y = pad.height / 2f
                }
                fun phase(name: String) = sendStatus(0, Bundle().apply { putString("phase", name) })
                var downTime = 0L
                fun send(action: Int, x: Float, touchY: Float) = runOnMainSync {
                    if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, touchY, 0)
                    check(pad.dispatchTouchEvent(event))
                    event.recycle()
                }
                fun contacts(action: Int, vararg points: Triple<Int, Float, Float>) = runOnMainSync {
                    if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
                    val properties = points.map { point -> MotionEvent.PointerProperties().apply {
                        id = point.first; toolType = MotionEvent.TOOL_TYPE_FINGER
                    } }.toTypedArray()
                    val coords = points.map { point -> MotionEvent.PointerCoords().apply {
                        this.x = point.second; this.y = point.third; pressure = 1f; size = .1f
                    } }.toTypedArray()
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, points.size,
                        properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                    check(pad.dispatchTouchEvent(event))
                    event.recycle()
                }
                phase("ready")
                // Host must first focus its disposable native ListView and place the cursor there.
                SystemClock.sleep(1200)
                try {
                    if (scenario == "ordinary" || scenario == "drag") {
                        send(MotionEvent.ACTION_DOWN, startX, y)
                        if (scenario == "drag") {
                            send(MotionEvent.ACTION_UP, startX, y)
                            SystemClock.sleep(60)
                            send(MotionEvent.ACTION_DOWN, startX, y)
                        }
                        send(MotionEvent.ACTION_MOVE, endX, y + 20 * density)
                        phase("edge_hold")
                        SystemClock.sleep(600)
                        send(MotionEvent.ACTION_UP, endX, y + 20 * density)
                    } else if (scenario == "desktop_button") {
                        runOnMainSync { activity.findViewById<View>(R.id.control_desktop).performClick() }
                    } else if (scenario.startsWith("three_") || scenario.startsWith("four_")) {
                        val count=if(scenario.startsWith("three_"))3 else 4
                        val dx=when { scenario.endsWith("left")->-65f;scenario.endsWith("right")->65f;else->0f }
                        val dy=when { scenario.endsWith("up")->-65f;scenario.endsWith("down")->65f;else->0f }
                        fun ps(n:Int, moved:Boolean=false)=(0 until n).map { i ->
                            Triple(i+7,pad.width/2f+(i*45f-(count-1)*22.5f+if(moved)dx else 0f)*density,y+(if(moved)dy else 0f)*density)
                        }.toTypedArray()
                        contacts(MotionEvent.ACTION_DOWN,*ps(1))
                        for(n in 2..count)contacts(MotionEvent.ACTION_POINTER_DOWN or ((n-1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),*ps(n))
                        contacts(MotionEvent.ACTION_MOVE,*ps(count,true))
                        phase("gesture")
                        SystemClock.sleep(100)
                        for(n in count downTo 2)contacts(MotionEvent.ACTION_POINTER_UP or ((n-1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),*ps(n,true))
                        contacts(MotionEvent.ACTION_UP,*ps(1,true))
                    } else {
                        val center = pad.width / 2f
                        fun a(span: Float, offsetY: Float = 0f) = Triple(7, center - span * density / 2, y + offsetY * density)
                        fun b(span: Float, offsetY: Float = 0f) = Triple(19, center + span * density / 2, y + offsetY * density)
                        val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                        contacts(MotionEvent.ACTION_DOWN, a(80f))
                        contacts(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), a(80f), b(80f))
                        when (scenario) {
                            "right_tap" -> {
                                contacts(pointerUp, a(80f), b(80f))
                                phase("first_lift")
                                SystemClock.sleep(40)
                                contacts(MotionEvent.ACTION_UP, a(80f))
                            }
                            "scroll" -> {
                                contacts(MotionEvent.ACTION_MOVE, a(80f, 30f), b(80f, 30f))
                                phase("gesture")
                                SystemClock.sleep(150)
                                contacts(pointerUp, a(80f, 30f), b(80f, 30f))
                                contacts(MotionEvent.ACTION_UP, a(80f, 30f))
                            }
                            "zoom" -> {
                                contacts(MotionEvent.ACTION_MOVE, a(100f), b(100f))
                                phase("gesture")
                                SystemClock.sleep(100)
                                contacts(MotionEvent.ACTION_MOVE, a(130f), b(130f))
                                SystemClock.sleep(150)
                                contacts(pointerUp, a(130f), b(130f))
                                contacts(MotionEvent.ACTION_UP, a(130f))
                            }
                        }
                    }
                    phase("released")
                    SystemClock.sleep(400)
                    result.putString("result", "PASS: real USB $scenario gesture sent; inspect the guarded host result")
                } finally {
                    send(MotionEvent.ACTION_CANCEL, endX, y + 20 * density)
                }
                runOnMainSync { activity.finish() }
                finish(Activity.RESULT_OK, result)
            } catch (e: Throwable) {
                activity?.let { host -> runOnMainSync { host.finish() } }
                result.putString("error", e.toString())
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (edgeMotionOnly || multiFingerOnly || shortcutsOnly) {
            val result = Bundle()
            var activity: Activity? = null
            try {
                // Shortcut layout checks use the actual adjustResize control window,
                // but replace its key callbacks with Capture before clicking buttons.
                activity = if(shortcutsOnly)openConnected() else startActivitySync(Intent(targetContext, SplashScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                if (shortcutsOnly) activity = verifyDesktopUi(activity)
                verifyPointerGestures(activity)
                verifyDragPointerTransitions(activity)
                verifyMultiFingerGestures(activity)
                verifySystemSwipes(activity)
                verifyEdgeMotion(activity)
                result.putString("result", "PASS: multi-finger, edge continuation and existing gesture regressions (fake sender)")
                finish(Activity.RESULT_OK, result)
            } catch (e: Throwable) {
                result.putString("error", android.util.Log.getStackTraceString(e))
                finish(Activity.RESULT_CANCELED, result)
            } finally {
                activity?.let { host -> runOnMainSync { host.finish() } }
            }
            return
        }
        if(feedbackPreview) {
            val result=Bundle()
            try {
                val activity=openConnected()
                runOnMainSync {
                    com.github.ifeel.keysjoy.ui.TouchFeedback.perform(activity.findViewById(R.id.simple_trackpad_touch))
                    fun buttons(v:View):List<Button> = if(v is Button)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{buttons(v.getChildAt(it))} else emptyList()
                    buttons(activity.window.decorView).first{it.text.toString()=="设置"}.performClick()
                }
                screenshot("feedback-settings")
                result.putString("result","PASS: system touch feedback requested and settings preview captured")
                finish(Activity.RESULT_OK,result)
            }catch(e:Throwable){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result)}
            return
        }
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
            if(landscapeImeOnly) {
                activity=orient(activity,true);keyboard(activity,true)
                verifyLandscapeIme(activity)
                uiPrefs.edit().putBoolean("keyboard",previousKeyboard).commit()
                result.putString("result","PASS: landscape system IME regression")
                finish(Activity.RESULT_OK,result);return
            }
            activity = orient(activity, false)
            verifyKeyboardUi(activity)
            verifyClipboardShortcuts(activity)
            result.putString("keyboard_ui", "PASS: native IME commits Chinese/emoji, keeps composition local, and emits deletion")
            verifyPointerGestures(activity)
            result.putString("gesture_semantics", "PASS: move/hold never select, tap-then-slide drags, double tap clicks, wheel down/up directions")
            verifyDragPointerTransitions(activity)
            verifyMultiFingerGestures(activity)
            verifyEdgeMotion(activity)
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
            result.putString("clipboard_shortcuts", "PASS: Ctrl+C/Ctrl+V in both layouts, orientations and transports; sticky modifiers reset")
            result.putString("landscape_ime", "PASS: system IME, composing/commit, close/Back, rapid toggle and rotation")
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
        runOnMainSync {
            check(activity.findViewById<View>(R.id.control_copy).isShown && activity.findViewById<View>(R.id.control_paste).isShown){"Clipboard buttons missing from layout"}
        }
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

    private fun verifyClipboardShortcuts(activity:Activity) {
        for(bluetooth in listOf(false,true))for(keyboard in listOf(false,true)) {
            val sent=mutableListOf<Pair<Int,Int>>()
            val modifiers=KeyboardModifiers()
            runOnMainSync {
                val screen=ControlScreen(activity,bluetooth,keyboard,modifiers)
                screen.onKey={usage,mask->sent.add(usage to mask)}
                modifiers.toggle(2)
                screen.copy.performClick()
                modifiers.toggle(4)
                screen.paste.performClick()
                check(modifiers.mask==0){"Clipboard action retained sticky modifiers"}
            }
            check(sent==listOf(6 to 1,25 to 1)){"Copy/paste must send Ctrl+C/Ctrl+V in every mode: $sent"}
        }
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
        verifyClipboardShortcuts(a)
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
        a=verifyLandscapeIme(a)
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

    private fun verifyLandscapeIme(initial:Activity):Activity {
        var a=initial
        fun awaitState(message:String,condition:()->Boolean) {
            repeat(50) {
                var ok=false
                runOnMainSync{ok=condition()}
                if(ok)return
                SystemClock.sleep(100)
            }
            error(message)
        }
        fun splitVisible()=a.findViewById<View>(R.id.control_left_panel)?.isShown==true && a.findViewById<View>(R.id.control_right_panel)?.isShown==true
        fun imeVisible()=androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.control_root))?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
        fun open() {
            runOnMainSync{a.findViewById<Button>(R.id.control_system_ime).performClick()}
            awaitState("Landscape IME did not appear"){imeVisible() && a.findViewById<View>(R.id.control_ime).isShown}
            waitForIdleSync();SystemClock.sleep(500)
            screenshot("control-landscape-system-ime")
            runOnMainSync {
                val root=a.findViewById<View>(R.id.control_root)
                val pad=a.findViewById<View>(R.id.simple_trackpad_touch)
                val left=a.findViewById<View>(R.id.btn_mouse_left)
                check(!splitVisible()){"Split keyboard overlaps system IME"}
                check(pad.width>root.width*0.55f && pad.height>=a.resources.displayMetrics.density*48){"IME touch surface unusable: ${pad.width}x${pad.height}"}
                check(left.isShown && left.height>0)
            }
        }
        awaitState("Landscape split keyboard opened IME automatically"){splitVisible() && !imeVisible()}
        runOnMainSync{check(a.findViewById<Button>(R.id.control_system_ime).text.toString()=="系统输入法")}
        open()
        verifyKeyboardUi(a)
        screenshot("control-landscape-system-ime")
        // Simulate the IME's own hide arrow, rather than the app's close button.
        runOnMainSync{a.findViewById<com.github.ifeel.keysjoy.ui.RemoteImeEditor>(R.id.control_ime).hideKeyboard()}
        awaitState("IME hide did not restore split keyboard"){splitVisible() && !imeVisible()}
        open()
        runOnMainSync{a.findViewById<View>(R.id.control_ime_hide).performClick()}
        awaitState("Close did not restore split keyboard"){splitVisible() && !imeVisible()}
        open()
        runOnMainSync {
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK))
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK))
        }
        awaitState("Back left the control page or failed to close IME"){!a.isFinishing && splitVisible() && !imeVisible()}
        runOnMainSync {
            a.findViewById<View>(R.id.control_system_ime).performClick()
            a.findViewById<View>(R.id.control_system_ime).performClick()
        }
        SystemClock.sleep(500)
        awaitState("Rapid toggle left a delayed IME"){splitVisible() && !imeVisible()}
        open()
        a=orient(a,false)
        awaitState("Portrait IME missing after rotation"){imeVisible() && a.findViewById<View>(R.id.control_ime).isShown}
        a=orient(a,true)
        awaitState("Rotation failed to restore landscape split mode"){splitVisible() && !imeVisible()}
        screenshot("control-landscape-system-ime-restored")
        return a
    }

    private class Capture : MouseInputSender {
        val moves = mutableListOf<Pair<Int, Int>>()
        var clicks = 0
        var haptics = 0
        val wheel = mutableListOf<Pair<Int,Int>>()
        val zooms = mutableListOf<Int>()
        val shortcuts = mutableListOf<TouchpadShortcut>()
        var scrolls = 0
        var rightClicks = 0
        var down = 0
        var up = 0
        override fun sendMouseMove(dx: Int, dy: Int) { moves.add(dx to dy) }
        override fun sendScroll(vscroll: Int, hscroll: Int) { scrolls++;wheel.add(vscroll to hscroll) }
        override fun sendZoom(steps: Int) { zooms.add(steps) }
        override fun sendShortcut(shortcut: TouchpadShortcut) { shortcuts.add(shortcut) }
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
            runOnMainSync {listener=ViewListener(capture).apply{mouseSpeed=1f;scrollSpeed=0.1f;rampEnabled=false;onHaptic={capture.haptics++}};view=View(activity)}
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
            check(c.haptics==0){"Movement must not vibrate"}
            check(c.down==0&&c.up==0&&c.clicks==0&&c.moves.isNotEmpty()){ "Plain move became a drag" }
        }
        scenario{_,c,send->
            send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            check(c.haptics==1){"First tap must acknowledge immediately"}
            check(c.clicks==0){"First tap must wait for drag classification"}
            SystemClock.sleep(80);send(MotionEvent.ACTION_DOWN,200f,400f,false)
            send(MotionEvent.ACTION_MOVE,230f,440f,false)
            check(c.clicks==0&&c.down==1&&c.up==0&&c.moves.sumOf{it.second}==40){"Second-tap slide did not drag"}
            send(MotionEvent.ACTION_CANCEL,230f,440f,false)
            check(c.up==1){"Cancelled drag did not release"}
            check(c.haptics==2){"Drag must acknowledge once on entry"}
        }
        scenario{_,c,send->
            send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            SystemClock.sleep(80);send(MotionEvent.ACTION_DOWN,200f,400f,false)
            SystemClock.sleep(50);send(MotionEvent.ACTION_UP,200f,400f,false)
            check(c.haptics==2){"Double tap must acknowledge each tap exactly once"}
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
            check(c.haptics==0){"Scrolling must not vibrate continuously"}
            check(c.down==0&&c.clicks==0&&c.moves.isEmpty()){ "Wheel gesture leaked mouse events" }
        }
    }

    private fun verifyMultiFingerGestures(activity: Activity) {
        fun p(id: Int, x: Float, y: Float = 100f) = Triple(id, x, y)
        val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        fun scenario(name: String, test: (ViewListener, Capture, (Int, List<Triple<Int, Float, Float>>) -> Unit) -> Unit) {
            val capture = Capture()
            lateinit var listener: ViewListener
            lateinit var view: View
            val density = activity.resources.displayMetrics.density
            runOnMainSync {
                listener = ViewListener(capture).apply { mouseSpeed = 1f; scrollSpeed = .1f; rampEnabled = false; onHaptic = {} }
                view = View(activity)
            }
            var downTime = 0L
            val send = { action: Int, points: List<Triple<Int, Float, Float>> ->
                runOnMainSync {
                    if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
                    val properties = points.map { point -> MotionEvent.PointerProperties().apply {
                        id = point.first; toolType = MotionEvent.TOOL_TYPE_FINGER
                    } }.toTypedArray()
                    val coords = points.map { point -> MotionEvent.PointerCoords().apply {
                        x = point.second * density; y = point.third * density; pressure = 1f; size = .1f
                    } }.toTypedArray()
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, points.size,
                        properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                    listener.onTouch(view, event)
                    event.recycle()
                }
            }
            try {
                test(listener, capture, send)
                SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 30L)
                runOnMainSync { check(capture.clicks == 0 && capture.down == 0 && capture.up == 0) {
                    "$name emitted a delayed left click or drag"
                } }
            } finally { runOnMainSync { listener.cancelAndRelease() } }
        }
        fun start(send: (Int, List<Triple<Int, Float, Float>>) -> Unit) {
            send(MotionEvent.ACTION_DOWN, listOf(p(7, 50f)))
            send(pointerDown, listOf(p(7, 50f), p(19, 150f)))
        }
        fun end(send: (Int, List<Triple<Int, Float, Float>>) -> Unit, a: Triple<Int, Float, Float> = p(7, 50f), b: Triple<Int, Float, Float> = p(19, 150f)) {
            send(pointerUp, listOf(a, b))
            send(MotionEvent.ACTION_UP, listOf(a))
        }
        scenario("two-finger tap") { _, c, send ->
            start(send)
            send(pointerUp, listOf(p(7, 50f), p(19, 150f)))
            check(c.rightClicks == 0) { "Right click fired before final lift" }
            send(MotionEvent.ACTION_UP, listOf(p(7, 50f)))
            check(c.rightClicks == 1 && c.moves.isEmpty() && c.wheel.isEmpty() && c.zooms.isEmpty())
        }
        scenario("long hold") { _, c, send ->
            start(send); SystemClock.sleep(320); end(send)
            check(c.rightClicks == 0 && c.wheel.isEmpty() && c.zooms.isEmpty())
        }
        scenario("parallel scroll and remaining finger") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_MOVE, listOf(p(19, 180f, 140f), p(7, 80f, 140f)))
            check(c.wheel.any { it.first > 0 && it.second > 0 } && c.zooms.isEmpty())
            send(MotionEvent.ACTION_POINTER_UP, listOf(p(19, 180f, 140f), p(7, 80f, 140f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 280f, 300f)))
            send(MotionEvent.ACTION_UP, listOf(p(7, 280f, 300f)))
            check(c.rightClicks == 0 && c.moves.isEmpty())
        }
        scenario("pinch and reversal") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 35f), p(19, 165f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 20f), p(19, 180f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 50f), p(19, 150f)))
            end(send)
            check(c.zooms.any { it > 0 } && c.zooms.any { it < 0 } && c.wheel.isEmpty())
            check(c.rightClicks == 0 && c.moves.isEmpty())
        }
        scenario("scroll locks mode") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 50f, 130f), p(19, 150f, 130f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 20f, 130f), p(19, 180f, 130f)))
            end(send, p(7, 20f, 130f), p(19, 180f, 130f))
            check(c.wheel.isNotEmpty() && c.zooms.isEmpty() && c.rightClicks == 0)
        }
        scenario("pinch locks mode") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 20f), p(19, 180f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 70f, 160f), p(19, 230f, 160f)))
            end(send, p(7, 70f, 160f), p(19, 230f, 160f))
            check(c.zooms.isNotEmpty() && c.wheel.isEmpty() && c.rightClicks == 0)
        }
        scenario("three and four fingers") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(p(7, 50f), p(19, 150f), p(30, 250f)))
            send(MotionEvent.ACTION_POINTER_DOWN or (3 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(p(7, 50f), p(19, 150f), p(30, 250f), p(40, 300f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 50f, 200f), p(19, 150f, 200f), p(30, 250f, 200f), p(40, 300f, 200f)))
            send(MotionEvent.ACTION_POINTER_UP or (3 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(p(7, 50f, 200f), p(19, 150f, 200f), p(30, 250f, 200f), p(40, 300f, 200f)))
            send(MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(p(7, 50f, 200f), p(19, 150f, 200f), p(30, 250f, 200f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 50f, 250f), p(19, 150f, 250f)))
            end(send, p(7, 50f, 250f), p(19, 150f, 250f))
            check(c.moves.isEmpty() && c.wheel.isEmpty() && c.zooms.isEmpty() && c.rightClicks == 0)
        }
        scenario("single movement then second finger") { _, c, send ->
            send(MotionEvent.ACTION_DOWN, listOf(p(7, 50f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 90f)))
            send(pointerDown, listOf(p(7, 90f), p(19, 150f)))
            end(send, p(7, 90f))
            check(c.rightClicks == 0)
        }
        scenario("remaining finger moves before UP") { _, c, send ->
            start(send)
            send(pointerUp, listOf(p(7, 50f), p(19, 150f)))
            send(MotionEvent.ACTION_UP, listOf(p(7, 100f)))
            check(c.rightClicks == 0 && c.moves.isEmpty())
        }
        scenario("cancel pinch and start fresh") { _, c, send ->
            start(send)
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 20f), p(19, 180f)))
            send(MotionEvent.ACTION_CANCEL, listOf(p(7, 20f), p(19, 180f)))
            send(MotionEvent.ACTION_DOWN, listOf(p(7, 50f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 90f)))
            send(MotionEvent.ACTION_UP, listOf(p(7, 90f)))
            check(c.rightClicks == 0 && c.moves.isNotEmpty())
        }
        scenario("toolbar press cancels active two-finger gesture") { listener, c, send ->
            start(send)
            runOnMainSync { listener.setButtonHeld(true) }
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 50f, 150f), p(19, 150f, 150f)))
            send(MotionEvent.ACTION_MOVE, listOf(p(7, 20f, 150f), p(19, 180f, 150f)))
            end(send, p(7, 20f, 150f), p(19, 180f, 150f))
            runOnMainSync { listener.setButtonHeld(false) }
            check(c.wheel.isEmpty() && c.zooms.isEmpty() && c.rightClicks == 0 && c.moves.isEmpty())
        }
    }

    private fun verifySystemSwipes(activity: Activity) {
        val cases = listOf(
            Triple(3, -65f to 0f, TouchpadShortcut.NEXT_APP),
            Triple(3, 65f to 0f, TouchpadShortcut.PREVIOUS_APP),
            Triple(3, 0f to -65f, TouchpadShortcut.TASK_VIEW),
            Triple(3, 0f to 65f, null),
            Triple(4, -65f to 0f, TouchpadShortcut.NEXT_DESKTOP),
            Triple(4, 65f to 0f, TouchpadShortcut.PREVIOUS_DESKTOP),
            Triple(4, 0f to -65f, TouchpadShortcut.TASK_VIEW),
            Triple(4, 0f to 65f, TouchpadShortcut.SHOW_DESKTOP))
        for ((count, movement, action) in cases) for (cancel in listOf(false, true)) {
            val capture = Capture()
            lateinit var listener: ViewListener
            lateinit var view: View
            val density = activity.resources.displayMetrics.density
            runOnMainSync { listener = ViewListener(capture).apply { rampEnabled=false; onHaptic={} }; view=View(activity) }
            var downTime = 0L
            fun send(eventAction: Int, n: Int, dx: Float = 0f, dy: Float = 0f) = runOnMainSync {
                if (eventAction == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
                val properties = (0 until n).map { MotionEvent.PointerProperties().apply { id=it+7; toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords = (0 until n).map { i -> MotionEvent.PointerCoords().apply {
                    this.x=(60+i*50+dx)*density; this.y=(120+dy)*density; pressure=1f
                } }.toTypedArray()
                val event = MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),eventAction,n,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                listener.onTouch(view,event);event.recycle()
            }
            try {
                send(MotionEvent.ACTION_DOWN,1)
                for (n in 2..count) send(MotionEvent.ACTION_POINTER_DOWN or ((n-1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),n)
                send(MotionEvent.ACTION_MOVE,count,movement.first,movement.second)
                check(capture.shortcuts.isEmpty()) { "Swipe fired before lift" }
                if (cancel) send(MotionEvent.ACTION_CANCEL,count,movement.first,movement.second)
                else {
                    for (n in count downTo 2) send(MotionEvent.ACTION_POINTER_UP or ((n-1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),n,movement.first,movement.second)
                    send(MotionEvent.ACTION_UP,1,movement.first,movement.second)
                }
                val expected = if (cancel || action==null) emptyList() else listOf(action)
                check(capture.shortcuts==expected) { "Unexpected $count-finger action: ${capture.shortcuts}, expected $expected" }
                check(capture.moves.isEmpty() && capture.wheel.isEmpty() && capture.zooms.isEmpty() && capture.rightClicks==0 && capture.clicks==0 && capture.down==0)
            } finally { runOnMainSync { listener.cancelAndRelease() } }
        }
    }

    private fun verifyDesktopUi(initial: Activity): Activity {
        var activity=initial
        val activityName=initial.javaClass.name
        val original=initial.resources.configuration.orientation
        for (wide in listOf(false,true)) {
            val wanted=if(wide)android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
            if(activity.resources.configuration.orientation!=wanted) activity=nextActivity(activityName) {
                activity.requestedOrientation=if(wide)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            var previousScreen:ControlScreen?=null
            for (bluetooth in listOf(false,true)) for (keyboard in listOf(false,true)) {
                lateinit var screen:ControlScreen
                val sent=mutableListOf<Pair<Int,Int>>()
                val modifiers=KeyboardModifiers()
                runOnMainSync {
                    previousScreen?.editor?.hideKeyboard()
                    activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    screen=ControlScreen(activity,bluetooth,keyboard,modifiers)
                    screen.onKey={usage,mask->sent.add(usage to mask)}
                    screen.connection(true)
                    activity.setContentView(screen.root)
                    previousScreen=screen
                }
                waitForIdleSync();SystemClock.sleep(100)
                fun checkButton() = runOnMainSync {
                    check(screen.desktop.isShown && screen.desktop.width>=32*activity.resources.displayMetrics.density) { "Desktop button hidden or too narrow" }
                    check(screen.desktop.layout?.lineCount==1) { "Desktop button text wrapped" }
                    val visible=android.graphics.Rect()
                    val location=IntArray(2)
                    screen.desktop.getGlobalVisibleRect(visible)
                    screen.desktop.getLocationOnScreen(location)
                    val baseline=location[1]+screen.desktop.baseline
                    check(baseline+screen.desktop.paint.fontMetrics.top>=visible.top && baseline+screen.desktop.paint.fontMetrics.bottom<=visible.bottom) {
                        "Desktop text clipped: view=${screen.desktop.width}x${screen.desktop.height}, baseline=$baseline, visible=$visible"
                    }
                    modifiers.toggle(1);modifiers.toggle(4)
                    screen.desktop.performClick()
                    check(modifiers.mask==0 && sent.last()==(7 to 8)) { "Desktop button did not send Win+D and reset modifiers" }
                }
                checkButton()
                if(!bluetooth)screenshot("desktop-${if(wide)"landscape" else "portrait"}-${if(keyboard)"keyboard" else "touchpad"}")
                if(wide && keyboard) {
                    runOnMainSync { screen.systemIme.performClick() }
                    SystemClock.sleep(600);waitForIdleSync();checkButton()
                    if(!bluetooth)screenshot("desktop-landscape-ime")
                    runOnMainSync { screen.systemIme.performClick() }
                    waitForIdleSync()
                }
            }
        }
        if(activity.resources.configuration.orientation!=original) activity=nextActivity(activityName) {
            activity.requestedOrientation=if(original==android.content.res.Configuration.ORIENTATION_LANDSCAPE)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        return activity
    }

    private fun verifyEdgeMotion(activity: Activity) {
        val capture = Capture()
        lateinit var listener: ViewListener
        lateinit var view: View
        val density = activity.resources.displayMetrics.density
        runOnMainSync {
            listener = ViewListener(capture).apply { mouseSpeed = 1f; rampEnabled = false; onHaptic = {} }
            view = View(activity).apply { setOnTouchListener(listener) }
            activity.addContentView(view, ViewGroup.LayoutParams((200 * density).toInt(), (200 * density).toInt()))
        }
        waitForIdleSync()
        runOnMainSync { check(view.width > 48 * density && view.height > 48 * density) }
        var downTime = 0L
        fun send(action: Int, x: Float, y: Float = 100f) {
            runOnMainSync {
                if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x * density, y * density, 0)
                check(view.dispatchTouchEvent(event))
                event.recycle()
            }
        }
        fun clear() = runOnMainSync { listener.cancelAndRelease(); capture.moves.clear() }
        fun stroke() { send(MotionEvent.ACTION_DOWN, 100f); send(MotionEvent.ACTION_MOVE, 195f) }
        fun arm() { runOnMainSync { listener.setButtonHeld(true) }; stroke() }
        fun moving(): Boolean {
            // This must produce output before the former 150ms dwell threshold.
            SystemClock.sleep(80)
            var moved = false
            runOnMainSync {
                moved = capture.moves.isNotEmpty()
                check(capture.moves.all { it.first >= 0 && it.second == 0 }) { "Continuation changed direction" }
            }
            return moved
        }
        fun clearMoves() = runOnMainSync { capture.moves.clear() }
        fun stopped() {
            clearMoves()
            SystemClock.sleep(400)
            runOnMainSync { check(capture.moves.isEmpty()) { "Continuation survived stop condition" } }
        }
        try {
            runOnMainSync { listener.setButtonHeld(true) }
            send(MotionEvent.ACTION_DOWN, 195f)
            stopped()
            send(MotionEvent.ACTION_CANCEL, 195f)
            clear()

            stroke(); stopped(); clear()
            runOnMainSync { listener.setButtonHeld(true, false) }
            stroke(); stopped(); clear()

            // Pressing Left after the pad contact began must also allow edge dragging.
            send(MotionEvent.ACTION_DOWN, 100f)
            runOnMainSync { listener.setButtonHeld(true) }
            send(MotionEvent.ACTION_MOVE, 195f); clearMoves(); check(moving())
            runOnMainSync { listener.setButtonHeld(false) }
            stopped(); clear()

            arm(); clearMoves()
            check(moving()) { "Held-button edge motion did not start immediately without MOVE events" }
            runOnMainSync { check(capture.down == 0 && capture.clicks == 0) { "Continuation emitted an extra button press" } }
            clearMoves()
            repeat(40) {
                send(MotionEvent.ACTION_MOVE, if (it % 2 == 0) 195.05f else 195f)
                SystemClock.sleep(5)
            }
            runOnMainSync { check(capture.moves.isNotEmpty()) { "Frequent jitter starved edge frames" } }
            send(MotionEvent.ACTION_UP, 195f); stopped(); clear()

            arm(); clearMoves(); check(moving())
            send(MotionEvent.ACTION_MOVE, 192f); stopped(); clear()

            arm(); clearMoves(); check(moving())
            send(MotionEvent.ACTION_CANCEL, 195f); stopped(); clear()

            arm(); clearMoves(); check(moving())
            runOnMainSync { listener.setButtonHeld(false); listener.setWheelMode(true) }
            stopped(); clear()

            arm(); clearMoves(); check(moving())
            runOnMainSync { listener.setButtonHeld(false) }
            stopped(); clear()

            arm(); clearMoves(); check(moving())
            runOnMainSync { listener.cancelAndRelease() }
            stopped(); clear()

            // Two physical contacts must cancel the single-contact continuation immediately.
            arm(); clearMoves(); check(moving())
            runOnMainSync {
                val properties = (0..1).map { MotionEvent.PointerProperties().apply { id = it } }.toTypedArray()
                val coords = listOf(195f, 150f).map { MotionEvent.PointerCoords().apply {
                    x = it * density; y = 100 * density; pressure = 1f
                } }.toTypedArray()
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
            stopped(); clear()

            // An already classified tap-drag keeps its one DOWN until the final UP.
            send(MotionEvent.ACTION_DOWN, 100f); send(MotionEvent.ACTION_UP, 100f)
            SystemClock.sleep(60)
            stroke(); clearMoves(); check(moving())
            runOnMainSync { check(capture.down == 1 && capture.up == 0 && capture.clicks == 0) }
            send(MotionEvent.ACTION_UP, 195f); stopped()
            runOnMainSync { check(capture.up == 1) }
            clear()

            arm(); clearMoves(); check(moving())
            runOnMainSync { view.layout(0, 0, view.width, view.height - (30 * density).toInt()) }
            stopped()
        } finally {
            runOnMainSync { listener.cancelAndRelease(); (view.parent as? ViewGroup)?.removeView(view) }
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
