# AMUG 0.3.0 RC1 Validation

## Automated gate

- Clean `testDebugUnitTest`, `assembleDebug`, and `lintDebug`: PASS
- Room v1→v2 schema export/migration build: PASS
- Python bridge and Windows BLE validation scripts compile: PASS
- `git diff --check`: PASS

## Pixel 10 Pro XL / Android 16

- Cold launch and foreground-service startup: PASS
- No input-dispatch/service ANR after alpha 3 fix: PASS
- Scan and connect to `S6-PLUS-0455`: PASS
- Version/status initialization with notification settle: PASS
- 8-second Bluetooth-off recovery and fresh status: PASS
- Rotation without duplicate connection: PASS
- 70-second background telemetry/service survival: PASS
- Quick Settings tile registration, disconnect, and reconnect: PASS
- Glance widget provider registration, zero polling period, resize support: PASS
- Foreground connected-device notification: PASS

## Direct physical BLE control

- Current/target/battery/charging/empty/status decode: PASS
- 130°F → 131°F → 130°F setpoint/readback: PASS
- Temperature hold enable/readback and physical warming trend: PASS
- Firmware auto-off 2h → 4h → 2h: PASS
- Hold-light and charge-light toggle/restore: PASS
- Music effects 1–6 and explicit off: PASS
- Ambient mode requires hold off, RGB readback, off, state restoration: PASS

## RC workflows

- Four-destination Material navigation and nested Technical screen: PASS
- Coffee/Tea/Other grouped catalog: PASS
- Exact, preset, custom preset, and learned suggestion flows: PASS build/tests
- Learning records only after command confirmation: PASS code/tests
- Opt-in alert preferences; no launch/scan notification prompt: PASS code/lint
- Sleep timer exposed with best-effort disclosure: PASS
- Diagnostics clear/share with address-redacted report: PASS

## Remaining for stable

- Pin an actual widget instance and visually verify compact/expanded launcher layouts.
- Perform TalkBack/switch-access session with a human tester.
- Use several days of real history to validate learned suggestion UX and ETA calibration.
- Replace debug signing with protected release signing before public stable distribution.

## RC2 additions

- Phone-supervised timer expanded to 5–120 minutes in 5-minute increments.
- Firmware 2/4-hour selection clarified as the separate hardware failsafe.
- Automatic firmware-empty safety-off plus manual empty override.
- Manual override physically validated through `0601` hold on, `0600` safety
  stop and `03` readback confirming hold off.
