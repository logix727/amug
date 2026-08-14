# AMUG — A better app for your VSITOO smart mug

Reverse engineering of the VSITOO smart self-heating mug BLE protocol so we can
build a better control app — no account, no cloud, just Bluetooth. The official
app (`com.jimiyoupin.vsitoo`, "VSITOO" on Google Play / App Store) is a DCloud
uni-app whose entire device logic is in minified JavaScript assets, which makes
protocol extraction straightforward.

## Device info (S6 Plus)

- 12 oz (355 ml) self-heating coffee mug, 316 stainless steel, IPX7
- Heats / holds 120–150 °F via AI auto-sensing (PID)
- Rechargeable, 2–4 h battery, charging coaster, RGB "music sync" LED
- Bluetooth 5.0 BLE
- Product page: https://www.vsitoo.com/products/s6-plus-smart-mug

## The "sucks" problems we can fix

- Flaky Bluetooth connection / discovery
- Confusing multi-device naming
- Heating-mode state not shown on the mug display
- Login / cloud requirement for a purely-BLE device

## BLE GATT profile (verified from decompiled app)

All VSITOO mug models use the `A3xx` profile. Two UUID families exist:

| Model | Service UUID | Chars |
|---|---|---|
| S6, S3 Pro, S1 Lite/Pro, JIMI-S2/S3 | `0000A300-0000-1000-8000-00805F9B34FB` | `0000A3xx` |
| S6 Plus, S3, S3 Plus, S3 Pro I, S5, S5 Pro, S3 Ultra, R1, R1 Pro, R2, K01, K02, H1 | `A3000000-0000-0000-0000-000000000000` | `A3xxxxxx` |

Characteristic roles are fixed by **array position** (not GATT properties):

- `CHAR[0]` = **WRITE** (commands)
- `CHAR[1]` = **NOTIFY** (telemetry / responses)
- `CHAR[2]` = firmware-update / reset channel

Exception: S1 Lite / S1 Pro notify on `0000A303`, write on `0000A301`.

The `0000A3xx` family additionally exposes `0000A304` / `0000A305` for temp
history reads.

Legacy JIMI-family mugs (H1/H1Pro/K01/K02/R1/R1Pro/R2/T1/T1Pro/C3/C3Pro/V1)
use a different profile: service `15F1E600-A277-43FC-A484-DD39EF8A9100`,
notify `…E601`, write `…E602`.

## Command protocol (S6 Plus, module `pagesControl/vsitoo-s6plus`)

Plain hex commands on `CHAR[0]`, responses come back on `CHAR[1]`. No checksum.
Status request `03`, firmware request `02`. On connect the app sends `02` then `03`.

### Status notification parse (opcode `03`)

```
byte  0  opcode (echo 03)
byte  1  unused / unknown
byte  2  flags: bit0 heating/workState, bit2 preventDryBurn+empty, bit4 charging
byte  3  current temperature integer part
byte  4  current temperature hundredths
byte  5  target temperature integer part
byte  6  target temperature hundredths
byte  7  securityWaitHours (255 = reset)
bytes 8-10 light color (RRGGBB)
byte 11  light mode
byte 12  battery (%)
byte 13  unused / unknown
bytes 14-15 battery-temperature ADC voltage (big-endian)
bytes 16-17 battery voltage (big-endian)
byte 18  hold-light mode
byte 19  charge-light mode
byte 20  night-light switch
```

### Commands (all via write to CHAR[0])

| Action | Bytes |
|---|---|
| Set temperature | `04` + intHex + fracHex (e.g. 54.44 °C → `04 36 2C`) |
| Gear (heating power) | `06` + gear (`0600` off, `0601`/`0602`/`0603`) |
| Hold / Idle | `0601` hold, `0600` idle |
| Music mode | `09` + mode (`0900`–`0905`); off = `0916` |
| Night light | `07` + RRGGBB + `01`/`00` |
| Hold / charge light | `0B00`/`0B01`, `0C00`/`0C01` |
| Disinfect | `0D01` |
| Security wait | `05` + hours hex (`0502`/`0504`) |
| Touch lock | `0F01` on / `0F00` off |
| Light time | `03` + sec hex |
| Clock | `02` + HH MM SS |

Other notify ops: `02`→firmware/hw, `16`(→`10`)→MAC, `20`→AI self-heating.

### S6 (non-Plus) differences

- Name regex: `/^Hi-\w{0,14}-.2ANU.{2}(\w{4})/`
- `02` firmware (parses version `a.b.c` + hw; hardware >= 1.1.4 shows heating switch)
- `03` status — 1-decimal temp; preTemp1/2/3, tempSn, remindDuration, securityWaitHours
- `05`+gear for gears; `0A00`/`0A01` heating on/off; `08` countdown request; `0A` noop
- OTA on CHAR[2] with `01FF`/`02FF` framing

## Connection flow (from decompiled `connect_common.js`)

1. `openBluetoothAdapter` → `createBLEConnection({ deviceId, timeout: 30000 })`
2. `getBLEDeviceServices` → find service whose `uuid.toUpperCase() === SERVICE_UUID` (retry after 100 ms)
3. `getBLEDeviceCharacteristics` (retry after 100 ms)
4. `notifyBLECharacteristicValueChange({ characteristicId: CHAR[1], state: true })`
5. after 500 ms → write `sendVersionCMD`; on response opcode `=== listenVersionCMD` → device registered
6. Control page enqueues init commands: status poll + firmware version (5 s fallback timer, re-send if no response)

### Write discipline

- All writes are write-with-response, serialized one at a time (`isWriting` single-flight)
- 3 retries per command; per-opcode dynamic timeout (500/800/1500/600 ms)
- 15 ms extra delay before writes on iOS
- Responses matched by first-byte opcode; same notification drives telemetry + command queue

## Reimplementation plan

- Replace login/cloud dependency with a direct BLE client (no account needed)
- Scan for devices advertising the `A300…` / `0000A300…` service (or name regex)
- Connect → discover → enable notify → send `02` + `03` → parse status
- Set temp: write `04 <int> <frac>`; control gear/hold/idle via `06`; music/night light via `09`/`07`
- No account, no cloud — pure local BLE

## Android app

AMUG now includes a native Kotlin app built for modern Pixels:

- Target / compile SDK 36 (Android 16)
- Jetpack Compose + Material 3 Expressive direction, edge-to-edge
- Pixel Material You dynamic color with automatic light/dark themes
- Nearby Devices permissions only; no Internet permission
- Broad Android BLE scan with `S6-PLUS-XXXX` name detection
- Native GATT connection, notification setup, and serialized writes
- Live temperature, target, battery, charging, heating, and empty-state display
- Fahrenheit by default with a persistent °F/°C switch
- Optional customizable temperature-responsive RGB ambient mode; the S6 Plus
  firmware pauses temperature hold while night-light/ambient mode is active
- S6 Plus custom temperature + gear controls
- Plain S6 status, heat switch, and low/medium/high preset controls

Build it with:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### v0.2 behavior

- Controls remain disabled until the mug returns a complete status snapshot.
- State-changing commands are confirmed through device status readback.
- Temperature hold, empty, charging, ambient mode, and connection state are
  shown separately; AMUG does not claim to know when the heater coil is active.
- Ambient temperature colors are fully customizable and intentionally pause
  temperature hold because that is how S6 Plus firmware implements night light.
- The last mug reconnects automatically with bounded retries and fresh-state
  reconciliation.
- Firmware, hardware, voltage, auto-off, and a bounded BLE event log are exposed
  in Diagnostics.

### v0.2 alpha 2 features

- One connected-device foreground service owns the only Android GATT connection.
- Ready, empty, low-battery, reconnect, and sleep-timer notifications.
- Phone-supervised 15/30/60-minute timers plus experimental firmware-backed
  2/4-hour auto-off.
- Six music-light effects, music off, hold light, and charging light controls.
- Pixel Quick Settings tile and responsive Glance home-screen widget.
- Multiple named mugs with per-mug presets, unit, ambient palette/preferences.
- Local Room session history with retention controls; no cloud or analytics.
- Windows read-only BLE validation tools in `tools/`.
- A documented stationary bridge reference in `bridge/` for a future legitimate
  Google Home Cloud-to-cloud integration. A phone-only Google Home relay is not
  technically or policy compliant.

### v0.2 alpha 4 Material redesign

The connected app is no longer one long engineering settings page. It uses a
Pixel-style Material 3 shell with Home, Drinks, Lighting, Settings, and
Technical destinations. Coffee/tea/other presets include canonical °F/°C
holding temperatures and descriptions; live temperature remains the first Home
content; protocol and roadmap details remain visible in Technical.

## Status

- [x] BLE protocol extracted and documented
- [x] Native Android 16 Material 3 app
- [x] Protocol unit tests and Android lint
- [x] Pixel 10 Pro XL install and cold-launch test
- [x] S6 Plus scan, GATT connect, and live telemetry validation
- [ ] Validate every write command safely on physical hardware
- [ ] Home Assistant / MQTT bridge

See [ROADMAP.md](ROADMAP.md) for planned revisions and
[HARDWARE_VALIDATION.md](HARDWARE_VALIDATION.md) for test evidence.

## Extracted artifacts

- Original APK pulled from device: `adb shell pm path com.jimiyoupin.vsitoo`
- App service JS: `assets/apps/__UNI__9E8AA0A/www/app-service.js`
- Control pages: `assets/apps/__UNI__9E8AA0A/www/pagesControl/app-sub-service.js`

## Hardware / FCC

- FCC filing `2A3WS-S3PRO` (Guangdong Jimi Youpin, Zhongshan) covers S3PRO/S2/S3/S5/S6 (not S6 Plus)
- Internal photos: https://fccid.io/2A3WS-S3PRO/Internal-Photos/Internal-Photos-5583247.pdf
- Test report: https://fccid.io/2A3WS-S3PRO/Test-Report/Test-Report-5583254.pdf
- S6 Plus manual: https://cdn.shopify.com/s/files/1/0572/6047/4445/files/S6_Plus-User_manual.pdf
- PCB silkscreen: `JIMIYOUPIN-S3PRO`, 2× INR18650 (2500 mAh, 7.4 V, 18.5 Wh) on S3Pro family
