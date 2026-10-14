# Experimental HFP + A2DP conflict management (test branch)

Status: **scaffold only; not wired into the active CarPlay lifecycle; off by default.**
No production release should claim automatic disconnection yet.

## Objective
After a verified CarPlay session, suppress the SAME iPhone's HFP and A2DP only, not the whole adapter, pairing, or iAP2 RFCOMM. After a confirmed final session end, restore only connections changed by this component.

## Current implementation
- `CarPlayAudioProfileGuard.kt` provides an opt-in flag (default false), reads bonded devices, and attempts per-profile methods by reflection.
- This is an experiment, NOT proven to work on production head units. Some Android versions block hidden methods or require platform privileges.
- No UI toggle or active session hook is installed. The class therefore does nothing in current builds.
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
