package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.compat.realSdkInt

/**
 * Opt-in legacy SoftAP request for API 16–23 (Android 4.1–6.0).
 * Uses the firmware's SAVED AP configuration; never replaces credentials, roots the
 * device, disables the adapter, or modifies network settings without user consent.
 */
internal object LegacyCarHotspotStarter {
    enum class Result { REQUESTED, ALREADY_ON, PERMISSION_REQUIRED, UNSUPPORTED, REJECTED }

    fun supportedSdk(api: Int): Boolean = api in 16..23

    fun start(context: Context, log: (String) -> Unit): Result {
        val api = realSdkInt()
        if (!supportedSdk(api)) return Result.UNSUPPORTED
        if (api >= 23 && !Settings.System.canWrite(context)) {
            log("legacy SoftAP: WRITE_SETTINGS app-op not granted")
            return Result.PERMISSION_REQUIRED
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return Result.UNSUPPORTED
        if (CarHotspotStatus.isEnabled(context) == true) return Result.ALREADY_ON
        return try {
            val method = wifi.javaClass.getMethod(
                "setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType
            )
            // A null configuration asks the OEM framework to reuse its stored SoftAP settings.
            // Do not create a new AP with invented SSID/password.
            val accepted = method.invoke(wifi, null, true) as? Boolean == true
            log("legacy SoftAP: api=$api requestAccepted=$accepted status=${CarHotspotStatus.isEnabled(context)}")
            if (accepted) Result.REQUESTED else Result.REJECTED
        } catch (error: SecurityException) {
            log("legacy SoftAP: permission denied ${error.javaClass.simpleName}")
            Result.PERMISSION_REQUIRED
        } catch (error: ReflectiveOperationException) {
            log("legacy SoftAP: unavailable ${error.javaClass.simpleName}, cause=${error.cause?.javaClass?.simpleName}")
            if (error.cause is SecurityException) Result.PERMISSION_REQUIRED else Result.UNSUPPORTED
        } catch (error: RuntimeException) {
            log("legacy SoftAP: failed ${error.javaClass.simpleName}")
            Result.REJECTED
        }
    }
}
