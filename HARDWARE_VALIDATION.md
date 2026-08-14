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
- v0.2 alpha 2 foreground service, notifications, timers, music/lights, tile,
  widget, multi-mug persistence, and session history

Windows validation tooling is available at `tools/ble_probe.py`. It performs a
read-only scan, connects to A300/A301/A302, and queries version/status. The PC's
Intel BLE adapter was confirmed working against nearby BLE devices, but the mug
was not advertising during the validation window (asleep or still owned by the
Pixel's single BLE connection).

## Direct Windows BLE validation — August 14, 2026

The mug later advertised as `S6-PLUS-0455` at address `4A:4D:04:00:04:55` and
was tested directly through the PC's Intel BLE adapter.

Verified GATT profile:

- Service: `A3000000-0000-0000-0000-000000000000`
- Notify/read: `A3020000-0000-0000-0000-000000000000`
- Write/write-with-response: `A3010000-0000-0000-0000-000000000000`
- OTA notify/read/write-without-response: `A3030000-0000-0000-0000-000000000000`

Read-only baseline:

- Current temperature: 54.44°C / 130.0°F
- Target temperature: 54.44°C / 130.0°F
- Empty: false
- Charging: true
- Battery: 100%
- Firmware auto-off: 2 hours
- RGB readback: `#FFD100`
- Night light: off

Reversible physical write/readback tests:

- Setpoint changed 130°F → 131°F and status confirmed 55.00°C.
- Setpoint restored 131°F → 130°F and status confirmed 54.44°C.
- Auto-off changed 2h → 4h and readback confirmed byte 7 = 4.
- Auto-off restored 4h → 2h and readback confirmed byte 7 = 2.
- Hold-light setting changed off → on → off with status confirmation.
- Charge-light setting changed off → on → off with status confirmation.

No ambient/night-light test was performed while coffee was being held because
the firmware intentionally disables temperature hold when night light is on.
The scripts used are `tools/validate_setpoint.py` and
`tools/validate_settings.py`.

Protocol research confirms the S6 Plus firmware automatically disables
temperature hold when night-light mode is enabled. AMUG therefore presents
temperature glow as an explicit ambient mode and never enables it silently.

## Safety rule

Until a command is physically validated, AMUG should either hide it behind a
diagnostic label or present it as experimental. Firmware update and reset frames
must not be exposed in normal builds.
