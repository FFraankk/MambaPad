// Modified for MambaPad (2026), based on KeysJoy. See LICENSE.
package com.github.ifeel.keysjoy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.github.ifeel.keysjoy.ui.MambaUi

class SplashScreenActivity: Activity() {

    companion object {
        private const val REQUEST_CODE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("Kontroller", "SplashScreen started")
        MambaUi.home(this,
            usb = { startActivity(Intent(this, UsbTouchpadActivity::class.java)) },
            bluetooth = { requestNeededPermissions() }
        )
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.e("Kontroller", "Requesting permissions: $permissions")
            requestPermissions(permissions.toTypedArray(), REQUEST_CODE)
        } else {
            Log.e("Kontroller", "Permissions OK, launching main")
            launchMainActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (allGranted(grantResults)) {
                launchMainActivity()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun allGranted(grantResults: IntArray) =
        grantResults.all { it == PackageManager.PERMISSION_GRANTED }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要附近设备权限")
            .setMessage("仅用于蓝牙连接和让电脑发现本机，不读取位置。你也可以返回并使用无需此权限的 USB 模式。")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("返回主页", null)
            .setCancelable(false)
            .show()
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, UsbTouchpadActivity::class.java).putExtra("transport", "bluetooth"))
    }
}
