package com.shilapi.xcertplay.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Experimental per-profile HFP/A2DP suppression. Does not disable Bluetooth, unpair,
 * or disconnect the base ACL/RFCOMM link. OFF by default.
 *
 * Only a positively identified single bonded iPhone is eligible. Hidden disconnectProfile
 * APIs are firmware-dependent and may be blocked; failure is non-fatal.
 */
class CarPlayAudioProfileGuard(private val context: Context) {
    companion object {
        private const val TAG = "CarPlayAudioGuard"
        private const val PREFS = "diplay_audio_profile_guard"
        private const val ENABLED = "enabled"
        fun enabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, enabled).apply()
        }
        internal fun isUnambiguousCandidate(names: List<String>): Boolean =
            names.size == 1 && names.single().contains("iPhone", ignoreCase = true)
    }

    private val lock = Any()
    private var ownedAddress: String? = null
    private val previouslyConnected = mutableSetOf<Int>()

    /** Call from a worker, never on the UI or Bluetooth binder thread. */
    @Suppress("MissingPermission")
    fun suppress(log: (String) -> Unit) {
        if (!enabled(context)) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            log("audio profile guard: missing BLUETOOTH_CONNECT")
            return
        }
        val candidates = try {
            adapter.bondedDevices.orEmpty().filter {
                it.name?.contains("iPhone", ignoreCase = true) == true
            }
        } catch (error: SecurityException) {
            log("audio profile guard: bonded devices not readable")
            return
        }
        if (!isUnambiguousCandidate(candidates.mapNotNull { it.name })) {
            log("audio profile guard: skip ambiguous iPhone identity")
            return
        }
        val device = candidates.single()
        synchronized(lock) {
            if (ownedAddress != null) return
            ownedAddress = device.address
        }
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            changeProfile(adapter, device, profile, false, log)
        }
    }

    /** Restore only profiles whose disconnect invocation was accepted during this session. */
    @Suppress("MissingPermission")
    fun restore(log: (String) -> Unit) {
        val address: String
        val profiles: List<Int>
        synchronized(lock) {
            address = ownedAddress ?: return
            profiles = previouslyConnected.toList()
            previouslyConnected.clear()
            ownedAddress = null
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = try { adapter.getRemoteDevice(address) } catch (_: IllegalArgumentException) { return }
        profiles.forEach { changeProfile(adapter, device, it, true, log) }
    }

    private fun changeProfile(
        adapter: BluetoothAdapter, device: BluetoothDevice, profile: Int,
        reconnect: Boolean, log: (String) -> Unit
    ) {
        val latch = CountDownLatch(1)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
                try {
                    val connected = proxy.connectedDevices.orEmpty().any { it.address == device.address }
                    if (reconnect || connected) {
                        // Deliberately reflect only on a live profile proxy; no global adapter shutdown.
                        val methodName = if (reconnect) "connect" else "disconnect"
                        val method = proxy.javaClass.methods.firstOrNull {
                            it.name == methodName && it.parameterTypes.contentEquals(arrayOf(BluetoothDevice::class.java))
                        } ?: proxy.javaClass.getDeclaredMethod(methodName, BluetoothDevice::class.java).apply { isAccessible = true }
                        val accepted = method.invoke(proxy, device) as? Boolean == true
                        if (!reconnect && accepted) synchronized(lock) {
                            if (ownedAddress == device.address) previouslyConnected.add(profile)
                        }
                        log("audio profile guard: profile=$profile reconnect=$reconnect accepted=$accepted")
                    }
                } catch (error: Exception) {
                    log("audio profile guard: profile=$profile unsupported=${error.javaClass.simpleName}")
                } finally {
                    runCatching { adapter.closeProfileProxy(id, proxy) }
                    latch.countDown()
                }
            }
            override fun onServiceDisconnected(id: Int) { latch.countDown() }
        }
        try {
            if (!adapter.getProfileProxy(context, listener, profile)) return
            if (!latch.await(2500, TimeUnit.MILLISECONDS)) {
                log("audio profile guard: profile=$profile timed out")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Profile operation failed", error)
            log("audio profile guard: profile=$profile error=${error.javaClass.simpleName}")
        }
    }
}
