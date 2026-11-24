package com.github.ifeel.keysjoy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import com.github.ifeel.keysjoy.input.KeyboardModifiers
import com.github.ifeel.keysjoy.input.MouseInputSender
import com.github.ifeel.keysjoy.input.RemoteInputEvent
import com.github.ifeel.keysjoy.listeners.ViewListener
import com.github.ifeel.keysjoy.senders.BluetoothInputSender
import com.github.ifeel.keysjoy.senders.UsbMouseSender
import com.github.ifeel.keysjoy.transport.AdbSocketTransport
import com.github.ifeel.keysjoy.ui.ControlScreen
import com.github.ifeel.keysjoy.ui.MambaUi
import com.github.ifeel.keysjoy.ui.MambaUi.dp

/** Shared page and navigation for both transports. No hardware Back interception. */
open class ControlActivity : Activity() {
    private var bluetooth=false
    private var keyboard=false
    private var speed=2
    private val speeds=floatArrayOf(0.4f,0.6f,0.8f,1.1f,1.5f,2.0f)
    private lateinit var screen:ControlScreen
    private val modifiers=KeyboardModifiers()
    private val held=mutableSetOf<String>()
    private var gesture:ViewListener?=null
    private var mouse:MouseInputSender?=null
    private var usb:AdbSocketTransport?=null
    private var bt:BluetoothInputSender?=null
    private var generation=0
    private var connected=false
    private var leaving=false
    private var lastToast=0L
    private val prefs by lazy {getSharedPreferences("control_ui",MODE_PRIVATE)}

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        bluetooth=intent.getStringExtra("transport")=="bluetooth"
        keyboard=prefs.getBoolean("keyboard",false)
        speed=prefs.getInt("speed",getSharedPreferences("usb",MODE_PRIVATE).getInt("mouse_speed",2)).coerceIn(speeds.indices)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){goHome()}
        rebuildUi()
    }
    private fun rebuildUi() {
        runCatching{gesture?.cancelAndRelease()}
        gesture=null;held.clear();modifiers.reset()
        if(::screen.isInitialized)screen.editor?.hideKeyboard()
        screen=ControlScreen(this,bluetooth,keyboard,modifiers)
        setContentView(screen.root)
        screen.back.setOnClickListener{goHome()}
        screen.layout.setOnClickListener {
            keyboard=!keyboard;prefs.edit().putBoolean("keyboard",keyboard).apply();rebuildUi()
        }
        screen.rotate.setOnClickListener {
            requestedOrientation=if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        screen.settings.setOnClickListener{settings()}
        screen.connect.setOnClickListener{connectionAction()}
        screen.onKey={usage,mask->screen.editor?.resetContext();sendKey(usage,mask)}
        screen.onText={count,text->sendText(count,text)}
        screen.editor?.postDelayed({screen.editor?.showKeyboard()},200)
        screen.connection(connected)
        bindMouse()
    }
    override fun onResume(){super.onResume();startSession()}
    private fun startSession() {
        stopSession()
        val current=generation
        if(!bluetooth) {
            val transport=AdbSocketTransport {message->runOnUiThread {
                if(current==generation) {
                    val active=message.startsWith("Connected")
                    if(connected&&!active){runCatching{gesture?.cancelAndRelease()};held.clear();modifiers.reset()}
                    connected=active
                    screen.connection(active,if(message.startsWith("Connecting"))"正在连接" else "等待 USB 连接")
                }
            }}
            usb=transport;mouse=UsbMouseSender(transport);bindMouse()
        } else {
            if(Build.VERSION.SDK_INT>=31&&(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)) {
                screen.connection(false,"请返回主页授权附近设备");return
            }
            val adapter=BluetoothAdapter.getDefaultAdapter()
            if(adapter==null||!adapter.isEnabled){screen.connection(false,"蓝牙未开启");return}
            var triedLast=false
            BluetoothController.autoPairFlag=false
            BluetoothController.statusListener={message->runOnUiThread {
                if(current==generation) {
                    screen.connection(connected,if(message.contains("failed",true))"蓝牙连接失败，请重试" else "等待蓝牙连接")
                    val address=prefs.getString("last_bluetooth",null)
                    val hid=BluetoothController.btHid
                    if(!triedLast&&hid!=null&&address!=null&&!connected) {
                        triedLast=true
                        runCatching {adapter.bondedDevices.firstOrNull{it.address==address}?.let{hid.connect(it)}}
                    }
                }
            }}
            BluetoothController.getSender{hid,host->runOnUiThread {
                if(current==generation) {
                    runCatching{bt?.close()}
                    bt=BluetoothInputSender(hid,host);mouse=bt;connected=true
                    prefs.edit().putString("last_bluetooth",host.address).apply()
                    screen.connection(true);bindMouse()
                }
            }}
            BluetoothController.getDisconnector{runOnUiThread {
                if(current==generation) {
                    runCatching{gesture?.cancelAndRelease()};runCatching{bt?.close()}
                    bt=null;mouse=null;connected=false;held.clear();modifiers.reset()
                    screen.connection(false,"蓝牙已断开");bindMouse()
                }
            }}
            BluetoothController.init(this)
        }
    }
    private fun bindMouse() {
        runCatching{gesture?.cancelAndRelease()}
        val sender=mouse
        val listener=sender?.let{ViewListener(it).apply{mouseSpeed=speeds[speed];scrollSpeed=if(bluetooth)0.33f else 0.025f;rampEnabled=false}}
        gesture=listener
        screen.pad.setOnTouchListener{v,event->if(connected&&listener!=null)listener.onTouch(v,event) else true}
        fun button(view:View,name:String) {
            view.setOnTouchListener{v,event->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN->if(connected){held.add(name);listener?.setButtonHeld(true);button(name,true);v.isPressed=true}
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{held.remove(name);button(name,false);listener?.setButtonHeld(held.isNotEmpty());v.isPressed=false;if(event.actionMasked==MotionEvent.ACTION_UP)v.performClick()}
                };true
            }
        }
        button(screen.left,"left");button(screen.right,"right")
        screen.wheel.setOnTouchListener{v,event->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->if(connected&&held.isEmpty()){listener?.setWheelMode(true);listener?.onWheelButtonTouch(event);v.isPressed=true}
                MotionEvent.ACTION_MOVE->listener?.onWheelButtonTouch(event)
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{listener?.onWheelButtonTouch(event);listener?.setWheelMode(false);v.isPressed=false;if(event.actionMasked==MotionEvent.ACTION_UP)v.performClick()}
            };true
        }
    }
    private fun button(name:String,down:Boolean) {
        if(bluetooth)runCatching{bt?.button(name,down)} else usb?.send(RemoteInputEvent.Button(name,down))
    }
    private fun sendKey(usage:Int,mask:Int) {
        if(!connected){hint("请先连接电脑");return}
        if(bluetooth)bt?.key(usage,mask) else {
            val transport=usb?:return
            if(!transport.supportsKeyboard){hint("请更新电脑端 MambaPad.exe 以使用键盘");return}
            transport.send(RemoteInputEvent.Key(usage,true,mask))
            transport.send(RemoteInputEvent.Key(usage,false,0))
        }
    }
    private fun sendText(deleteBefore:Int,text:String) {
        if(!connected){screen.editor?.resetContext();hint("请先连接电脑");return}
        repeat(deleteBefore.coerceAtMost(2048)){sendKey(42,0)}
        val mask=modifiers.take()
        screen.resetModifiers()
        if(text.isEmpty())return
        if(bluetooth || mask!=0) {
            val keys=text.map{com.github.ifeel.keysjoy.input.AsciiHid.key(it)}
            if(keys.any{it==null}) {
                hint(if(bluetooth)"蓝牙键盘暂不支持手机直接发送中文，请用电脑输入法；USB 支持中文" else "组合键请使用英文字母")
                screen.editor?.resetContext();return
            }
            keys.filterNotNull().forEach{(usage,shift)->sendKey(usage,shift or mask)}
            if(mask!=0)screen.editor?.resetContext()
        } else {
            val transport=usb?:return
            if(!transport.supportsText){hint("请更新电脑端 MambaPad.exe 以支持系统输入法");screen.editor?.resetContext();return}
            // Bound each UTF-8 JSON line below the receiver's 4096-byte limit.
            val buffer=StringBuilder()
            fun flush(){if(buffer.isNotEmpty()){transport.send(RemoteInputEvent.Text(buffer.toString()));buffer.setLength(0)}}
            text.codePoints().forEach { cp ->
                if(cp==10||cp==13||cp==9){flush();sendKey(if(cp==9)43 else 40,0)}
                else if(cp>=32){buffer.appendCodePoint(cp);if(buffer.length>=128)flush()}
            }
            flush()
        }
    }
    private fun hint(message:String) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastToast>1500){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();lastToast=now}
    }
    private fun settings() {
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(10))}
        val label=MambaUi.text(this,"指针速度 ${speed+1}/6",14f)
        body.addView(label)
        body.addView(SeekBar(this).apply{
            max=5;progress=speed
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(bar:SeekBar,value:Int,fromUser:Boolean){speed=value;gesture?.mouseSpeed=speeds[speed];prefs.edit().putInt("speed",speed).apply();label.text="指针速度 ${speed+1}/6"}
                override fun onStartTrackingTouch(bar:SeekBar){}
                override fun onStopTrackingTouch(bar:SeekBar){}
            })
        })
        body.addView(MambaUi.text(this,"轻点点击，双指滚动，轻点后再按住拖动。\n按住滚轮键，在触控区滑动即可滚动。\nShift / Ctrl / Alt / Win 对下一个按键生效。\n竖屏使用手机系统输入法，触控板随键盘高度调整。USB 支持中文和表情；蓝牙文字使用电脑输入法。",12f,MambaUi.muted).apply{setLineSpacing(dp(4).toFloat(),1f)})
        AlertDialog.Builder(this).setTitle("操作设置").setView(body).setPositiveButton("完成",null)
            .setNeutralButton(if(bluetooth)"连接设备" else "重新连接"){_,_->connectionAction()}.show()
    }
    private fun connectionAction() {
        if(!bluetooth){startSession();return}
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){goHome();return}
        val adapter=BluetoothAdapter.getDefaultAdapter()
        if(adapter==null){hint("本机不支持蓝牙");return}
        if(!adapter.isEnabled){startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return}
        val devices=adapter.bondedDevices.toList()
        val labels=devices.map{it.name?:"已配对设备"}+"配对新电脑（允许发现本机）"
        AlertDialog.Builder(this).setTitle("选择电脑").setItems(labels.toTypedArray()){_,index->
            if(index==devices.size)startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120))
            else {
                prefs.edit().putString("last_bluetooth",devices[index].address).apply()
                val hid=BluetoothController.btHid
                if(hid==null)startSession() else runCatching{hid.connect(devices[index])}
            }
        }.setNegativeButton("取消",null).show()
    }
    private fun stopSession() {
        generation++
        runCatching{gesture?.cancelAndRelease()};gesture=null
        runCatching{bt?.close()};bt=null
        usb?.close();usb=null;mouse=null
        held.clear();modifiers.reset();connected=false
        if(bluetooth)runCatching{BluetoothController.endSession()}
    }
    private fun goHome() {
        if(leaving)return
        leaving=true;screen.editor?.hideKeyboard();stopSession()
        startActivity(Intent(this,SplashScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
    @Deprecated("Legacy Android back navigation")
    override fun onBackPressed(){goHome()}
    override fun onPause(){stopSession();super.onPause()}
    override fun onDestroy(){stopSession();super.onDestroy()}
}
