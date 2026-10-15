# Experimental automatic saved-car-hotspot startup (Android 4.1–6.0)

Branch: `test/legacy-hotspot-api16-23` (from main; not merged).

## Motivation
An AC8227L / Android 6.0 / API 23 report showed MANUAL hotspot startup waiting until HOTSPOT_NOT_READY. The existing automatic hotspot switch is restricted to BYD/ADB settings, so generic head units cannot easily opt in.

## Implementation
- All head units on API 16–23 get an **opt-in**, default-OFF switch in Connection setup.
- On API 23, when WRITE_SETTINGS app-op is missing, the app exposes a button to open Android's permission/settings page. Firmware may lack that page; use its device settings instead.
- For API 16–23 the starter attempts `WifiManager.setWifiApEnabled(null, true)` via reflection to reuse the **previously saved** hotspot configuration. It does not generate an SSID/password or disable the phone's/vehicle's global Bluetooth.
- It verifies readiness using the existing CarHotspotStatus/ManualHotspotManager path. An accepted request does NOT guarantee success.
- The existing authorized-ADB fallback remains available where applicable, and the app will report unsupported firmware if the private API is unavailable.
- For later OS releases the existing implementation is preserved.
- The Gradle minSdk declaration in mobile/common/shared was reduced from 17 to 16 **only for compatibility testing**.

## Important limits
- Firmware may restrict SoftAP APIs to system/privileged apps even if WRITE_SETTINGS is granted.
- Some OEMs require vendor Binder services or privileged permissions unavailable to a normal sideloaded APK. The feature does not silently escalate privileges.
- The app targets Android API 37; lowering minSdk may expose dependency/bytecode/resource limitations. Full API 16 install and runtime compatibility has **not** been demonstrated.
- The saved hotspot must first be set up manually in the car settings, and Wi-Fi/SoftAP hardware must support it.
- If the OS denies permission, do not retry indefinitely.
- Test on a parked vehicle before distributing builds.

## Verification checklist
1. Gradle sync, :shared:testDebugUnitTest, :common:testDebugUnitTest, :mobile:assembleDebug.
2. Install on API 16, 17, 19, 22 and 23 systems; report any dependency minSdk/VerifyError errors.
3. Verify no hotspot operations with switch off.
4. API 23: grant special WRITE_SETTINGS permission, relaunch, verify app-op.
5. Enable saved hotspot via car settings once, turn it off, then enable DiPlay auto hotspot.
6. Compare AP status before and after; check SSID/password unchanged.
7. Denied/unsupported firmware: non-crashing error and usable manual fallback.
8. Cold boot and app restart: no repeated uncontrolled radio toggling.
9. Confirm iPhone can join CarPlay over the activated hotspot.
