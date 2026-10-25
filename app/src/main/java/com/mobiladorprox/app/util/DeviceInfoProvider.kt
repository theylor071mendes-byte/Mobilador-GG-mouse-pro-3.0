package com.mobiladorprox.app.util

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Informações reais do dispositivo, sem estimativas nem promessas de desempenho. */
object DeviceInfoProvider {

    data class RamInfo(val availableMb: Long, val totalMb: Long, val lowMemory: Boolean)

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val sdkInt: Int
    )

    data class ConnectionInfo(
        val physicalKeyboardConnected: Boolean,
        val mouseConnected: Boolean,
        val bluetoothSupported: Boolean,
        val bluetoothEnabled: Boolean,
        val bluetoothPermissionGranted: Boolean,
        val bondedBluetoothDeviceCount: Int,
        val usbDeviceCount: Int
    )

    fun getRamInfo(context: Context): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamInfo(
            availableMb = info.availMem / (1024 * 1024),
            totalMb = info.totalMem / (1024 * 1024),
            lowMemory = info.lowMemory
        )
    }

    fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        manufacturer = Build.MANUFACTURER ?: "Desconhecido",
        model = Build.MODEL ?: "Desconhecido",
        androidVersion = Build.VERSION.RELEASE ?: "?",
        sdkInt = Build.VERSION.SDK_INT
    )

    /**
     * Taxa de atualização da tela, quando o sistema informa. Retorna null se
     * a API não disponibilizar esse dado no aparelho.
     */
    @Suppress("DEPRECATION")
    fun getRefreshRateHz(context: Context): Float? = runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.defaultDisplay?.refreshRate
    }.getOrNull()

    fun getConnectionInfo(context: Context): ConnectionInfo {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
        var keyboard = false
        var mouse = false
        inputManager?.inputDeviceIds?.forEach { id ->
            val device = InputDevice.getDevice(id) ?: return@forEach
            if (device.isVirtual) return@forEach
            val sources = device.sources
            if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            ) {
                keyboard = true
            }
            if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
                mouse = true
            }
        }

        val bluetoothSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        val bluetoothEnabled = adapter?.isEnabled == true
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val bondedCount = if (bluetoothEnabled && permissionGranted) {
            runCatching { adapter?.bondedDevices?.size ?: 0 }.getOrDefault(0)
        } else 0

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbDeviceCount = runCatching { usbManager?.deviceList?.size ?: 0 }.getOrDefault(0)

        return ConnectionInfo(
            physicalKeyboardConnected = keyboard,
            mouseConnected = mouse,
            bluetoothSupported = bluetoothSupported,
            bluetoothEnabled = bluetoothEnabled,
            bluetoothPermissionGranted = permissionGranted,
            bondedBluetoothDeviceCount = bondedCount,
            usbDeviceCount = usbDeviceCount
        )
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceClassName)
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
}
