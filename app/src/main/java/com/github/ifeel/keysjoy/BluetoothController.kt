// Modified for MambaPad (2026), based on KeysJoy. See LICENSE.
package com.github.ifeel.keysjoy

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.github.ifeel.keysjoy.reports.FeatureReport


@Suppress("MemberVisibilityCanBePrivate")
object BluetoothController: BluetoothHidDevice.Callback(), BluetoothProfile.ServiceListener {
    private var sessionRequested = false

    const val TAG = "BluetoothController"

    val featureReport = FeatureReport()



    override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
        try {
            Log.i("setfirst","setfirst")
            super.onSetReport(device, type, id, data)
            Log.i("setreport","this $device and $type and $id and $data")
        } catch (e: Throwable) {
            Log.e(TAG, "onSetReport crash", e)
        }
    }


    override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
        try {
            Log.i("getbefore", "first")
            super.onGetReport(device, type, id, bufferSize)

            Log.i("get", "second")
                if (type == BluetoothHidDevice.REPORT_TYPE_FEATURE) {
                    featureReport.wheelResolutionMultiplier = true
                    featureReport.acPanResolutionMultiplier = true
                    Log.i("getbthid","$btHid")

                     var wasrs=btHid?.replyReport(device, type, FeatureReport.ID, featureReport.bytes)
                    Log.i("replysuccess flag ",wasrs.toString())
                }
        } catch (e: Throwable) {
            Log.e(TAG, "onGetReport crash", e)
        }
    }


    val btAdapter by lazy {
        BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("BluetoothAdapter unavailable")
    }
    var btHid: BluetoothHidDevice? = null
    var hostDevice: BluetoothDevice? = null
    var autoPairFlag = false

    var statusListener: ((String) -> Unit)? = null

    private fun updateStatus(msg: String) {
        Log.i(TAG, "Status: $msg")
        statusListener?.invoke(msg)
    }

    var mpluggedDevice :BluetoothDevice? = null

    /** List of paired devices for device switching */
    val pairedDevices = mutableListOf<BluetoothDevice>()
    var targetSwitchDevice: BluetoothDevice? = null

    // Auto-reconnect loop fields
    private var reconnectHandler: Handler? = null
    private val RECONNECT_INTERVAL_MS = 5000L

    private var deviceListener: ((BluetoothHidDevice, BluetoothDevice)->Unit)? = null
    private var disconnectListener: (()->Unit)? = null

    /**
     * Start a periodic reconnect loop that retries every [RECONNECT_INTERVAL_MS]
     * until the device reconnects, autopair is disabled, or the plugged device goes null.
     */
    fun startAutoReconnect() {
        stopAutoReconnect()
        reconnectHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!autoPairFlag || mpluggedDevice == null) {
                    stopAutoReconnect()
                    return
                }
                val device = mpluggedDevice ?: return
                val state = btHid?.getConnectionState(device)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    stopAutoReconnect()
                    return
                }
                // Still disconnected, try reconnect
                Log.i(TAG, "Auto-reconnect attempt to ${device.name}")
                btHid?.connect(device)
                reconnectHandler?.postDelayed(this, RECONNECT_INTERVAL_MS)
            }
        }
        reconnectHandler?.post(runnable)
    }

    /**
     * Stop the auto-reconnect loop and release the handler.
     */
    fun stopAutoReconnect() {
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = null
    }

    fun init(ctx: Context) {
        sessionRequested = true
        if (btHid != null) {
            // Check if still connected — btHid may be stale after app switch
            try {
                if (btHid?.connectedDevices.isNullOrEmpty()) {
                    Log.w(TAG, "btHid has no connected devices, forcing re-init")
                    btHid = null
                    hostDevice = null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "btHid is stale, resetting", e)
                btHid = null
                hostDevice = null
            }
        }
        if (btHid != null) return
        try {
            btAdapter.getProfileProxy(ctx.applicationContext, this, BluetoothProfile.HID_DEVICE)
        } catch (e: Throwable) {
            Log.e(TAG, "getProfileProxy failed", e)
            updateStatus("BT init failed: ${e.message}")
        }
    }

    fun getSender(callback: (BluetoothHidDevice, BluetoothDevice)->Unit) {
        deviceListener = callback
        btHid?.let { hidd ->
            hostDevice?.let { host ->
                callback(hidd, host)
                return
            }
        }
        deviceListener = callback
    }


    fun getDisconnector(callback: ()->Unit) {

        disconnectListener = callback
    }

    /*****************************************************/
    /** BluetoothProfile.ServiceListener implementation **/
    /*****************************************************/

    override fun onServiceDisconnected(profile: Int) {
        try {
            Log.e(TAG, "Service disconnected!")
            if (profile == BluetoothProfile.HID_DEVICE)
                btHid = null
            updateStatus("BT service disconnected")
        } catch (e: Throwable) {
            Log.e(TAG, "onServiceDisconnected crash", e)
        }
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        if (!sessionRequested) {
            btAdapter.closeProfileProxy(profile, proxy)
            return
        }
        try {
            Log.i(TAG, "Connected to service")
            if (profile != BluetoothProfile.HID_DEVICE) {
                Log.wtf(TAG, "WTF? $profile")
                return
            }

            val btHid = proxy as? BluetoothHidDevice
            if (btHid == null) {
                Log.wtf(TAG, "WTF? Proxy received but it's not BluetoothHidDevice")
                return
            }
            this.btHid = btHid
            val registered = btHid.registerApp(sdpRecord, null, qosOut, {it.run()}, this)//--
            Log.i(TAG, "registerApp result: $registered")
            if (registered) {
                updateStatus("蓝牙已就绪，请在电脑上配对本机")
            } else {
                updateStatus("HID reg failed, check BT permissions")
            }
            // Request discoverability only through Android's user-confirmed UI.
        } catch (e: Throwable) {
            Log.e(TAG, "onServiceConnected crash", e)
        }
    }



    /************************************************/
    /** BluetoothHidDevice.Callback implementation **/
    /************************************************/



    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        if (!sessionRequested) return
        try {
            super.onConnectionStateChanged(device, state)
            Log.i(TAG, "Connection state ${when(state) {
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"

                else -> state.toString()
            }}")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if (device != null) {
                    hostDevice = device
                    stopAutoReconnect()
                    device?.let { dev ->
                        if (pairedDevices.none { it.address == dev.address }) {
                            pairedDevices.add(dev)
                        }
                    }

                    deviceListener?.invoke(btHid!!, device)
                    updateStatus("Connected: ${device.name}")

                    //deviceListener = null
                } else {
                    Log.e(TAG, "Device not connected")
                }
            } else {
                hostDevice = null
                val toSwitch = targetSwitchDevice
                if (toSwitch != null) {
                    targetSwitchDevice = null
                    Log.i(TAG, "Switching to device: ${toSwitch.name}")
                    updateStatus("Switching...")
                    btHid?.connect(toSwitch)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnectListener?.invoke()
                    updateStatus("Disconnected, waiting...")
                    if (autoPairFlag && mpluggedDevice != null) {
                        Log.i(TAG, "Device disconnected, starting auto-reconnect loop")
                        startAutoReconnect()
                    }
                }

            }
        } catch (e: Throwable) {
            Log.e(TAG, "onConnectionStateChanged crash", e)
        }
    }

    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        if (!sessionRequested) return
        try {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.i(TAG, "onAppStatusChanged: registered=$registered, device=$pluggedDevice")
            
            if (registered) {
                mpluggedDevice = pluggedDevice
                pluggedDevice?.let { dev ->
                    if (pairedDevices.none { it.address == dev.address }) {
                        pairedDevices.add(dev)
                    }
                }
                updateStatus("HID app registered")
                if (autoPairFlag && pluggedDevice != null) {
                    try {
                        btHid?.connect(pluggedDevice)
                        Log.i(TAG, "Auto-connecting to previously paired device: ${pluggedDevice?.name}")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Auto-connect in onAppStatusChanged failed", e)
                    }
                }
            } else {
                updateStatus("HID app unregistered, re-registering...")
                btHid?.let { hid ->
                    try {
                        hid.registerApp(sdpRecord, null, qosOut, { it.run() }, this)
                        Log.i(TAG, "Re-register attempt sent")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Re-register failed", e)
                        updateStatus("HID re-register failed: ${e.message}")
                        btHid = null
                        hostDevice = null
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "onAppStatusChanged crash", e)
        }
    }





    /*************/
    /** Garbage **/
    /*************/

    fun endSession() {
        sessionRequested = false
        autoPairFlag = false
        stopAutoReconnect()
        deviceListener = null
        disconnectListener = null
        statusListener = null
        val hid = btHid
        val host = hostDevice
        btHid = null
        hostDevice = null
        if (hid != null) {
            try { if (host != null) hid.disconnect(host) } catch (_: SecurityException) {}
            try { hid.unregisterApp() } catch (_: SecurityException) {}
            try { btAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) } catch (_: Exception) {}
        }
    }

    private val sdpRecord by lazy {
        BluetoothHidDeviceAppSdpSettings(
            "MambaPad",
            "Phone touchpad and keyboard",
            "MambaPad",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            DescriptorCollection.MOUSE_KEYBOARD_COMBO
        )
    }



    /**
     * Switch to a different paired device.
     * Disconnects current device; connection to target happens
     * automatically in onConnectionStateChanged callback.
     */
    fun switchTo(device: BluetoothDevice) {
        Log.i(TAG, "switchTo: requested switch to ${device.name}")
        if (device.address == hostDevice?.address) {
            Log.i(TAG, "switchTo: already connected to ${device.name}, skipping")
            return
        }
        targetSwitchDevice = device
        stopAutoReconnect()
        hostDevice?.let { btHid?.disconnect(it) }
            ?: run {
                // No current connection, connect directly
                targetSwitchDevice = null
                btHid?.connect(device)
            }
    }

    private val qosOut by lazy {
        BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )
    }

}
