# Hardware Validation

## Test setup

- Phone: Google Pixel 10 Pro XL (`mustang`)
- Android target: API 36 / Android 16
- App: AMUG `0.1.0`, package `dev.logix.amug`
- Mug: VSITOO S6 Plus advertising as `S6-PLUS-0455`
- Mug condition: powered on and sitting on the charging coaster
- Connection: local Bluetooth Low Energy; AMUG has no Internet permission

## Verified on August 13, 2026

- Debug APK installed over ADB successfully.
- Cold launch completed in approximately 1.7 seconds.
- Activity remained foreground/resumed with no `AndroidRuntime` crash.
- Android Nearby Devices permissions were granted.
- AMUG discovered the mug by its advertised name.
- AMUG connected over BLE without pairing or bonding.
- Service discovery found the S6 Plus A300 service.
- A302 notifications were enabled successfully.
- Android Bluetooth diagnostics showed AMUG as the active GATT client for
  `S6-PLUS-0455`.
- A fresh status request returned live telemetry:
  - Current temperature: 29 °C
  - Target temperature: 54 °C
  - Battery: 100%
  - Reported state: holding / not actively heating

## Not yet verified

- Target-temperature write acceptance and persistence
- Gear 0–3 behavior
- Difference between heating enabled, actively heating, and holding
- Empty/dry-burn transitions
- Charger removal and reconnect behavior
- RGB, music mode, night light, safety wait, and touch lock
- Firmware/hardware version display
- Plain S6 hardware
- Long-running background operation
- Temperature-glow RGB writes and interaction with music/night-light modes
- v0.2 serialized GATT state machine, command confirmation, and reconnect flow
- v0.2 exact target, presets, 2/4-hour safety-wait writes, and ambient-mode UI

Protocol research confirms the S6 Plus firmware automatically disables
temperature hold when night-light mode is enabled. AMUG therefore presents
temperature glow as an explicit ambient mode and never enables it silently.

## Safety rule

Until a command is physically validated, AMUG should either hide it behind a
diagnostic label or present it as experimental. Firmware update and reset frames
must not be exposed in normal builds.
