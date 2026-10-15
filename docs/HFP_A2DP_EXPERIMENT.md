# Experimental HFP + A2DP conflict management (test branch)

Status: **experimental; connected to CarPlay session callbacks; on by default in the test branch.**
No production release should claim automatic disconnection yet.

## Objective
After a verified CarPlay session, suppress the SAME iPhone's HFP and A2DP only, not the whole adapter, pairing, or iAP2 RFCOMM. After a confirmed final session end, restore only connections changed by this component.

## Current implementation
- `CarPlayAudioProfileGuard.kt` provides an opt-in flag (default false), reads bonded devices, and attempts per-profile methods by reflection.
- This is an experiment, NOT proven to work on production head units. Some Android versions block hidden methods or require platform privileges.
- A toggle is available under Advanced media; it defaults on only in this test branch. Session start and end callbacks invoke the guard. If wireless tunneling is not ready when the session starts, the current version skips suppression instead of retrying later.
- The current unique-"iPhone" name heuristic is not sufficient to authenticate the active CarPlay device. DO NOT activate this prototype until the peer identity is explicitly tied to the session.
- Profile method return values do not prove the link changed; add state observations before accepting a disconnect/restoration.
- Never call the blocking profile-proxy wait on the UI thread.
- Add session-generation fencing and delayed confirmation of wireless tunnel handoff. Restore only after the final session has ended and reconnect is not scheduled.
- HSAE `disConnect()` is NOT a per-profile method and must not be used as HFP-only before device-specific testing.
- Preserve any ongoing calls and do not repeatedly fight firmware auto-connect without bounded retry.

## Planned acceptance tests
1. Disable flag: zero Bluetooth profile mutations.
2. One paired iPhone is not automatically proof of matching CarPlay device identity.
3. Wired session activation: HFP and A2DP disconnect only after verified peer/session.
4. Wireless startup: wait until Wi-Fi transport and iAP2 handoff are stable.
5. Calls/music switch correctly, no pairing deletion or adapter disable.
6. Different iPhone and passenger phones stay connected.
7. End/reconnect race does not restore profiles prematurely.
8. Hidden API missing / permission denied: graceful skip.
9. Firmware reconnect loops are bounded and logged.
10. Crash/restart recovery never reconnects an unrelated peer.

## 2026-10-15 wireless timing revision
- Require the first decoded/rendered AirPlay video frame and successful tunneled iAP2 handoff before attempting HFP/A2DP disconnect.
- Schedule a one-second session-stability check after both events. Cancel by rechecking session identity and wireless generation before acting.
- Resolve the Bluetooth target by exact selected wireless iPhone address, not a unique name containing 'iPhone'. Ambiguous/unbonded addresses fail closed.
- Add before/immediate-after profile state diagnostics. An immediate state read is not proof of final disconnection: Bluetooth profile changes are asynchronous.
- If the session has no selected Bluetooth address (e.g., Wi-Fi-only startup), skip instead of guessing.
- Wired-session profile suppression is not included in this timing revision and must be wired separately.
- Changes are committed but **not built or tested on hardware**.
