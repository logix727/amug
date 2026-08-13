# AMUG Roadmap

AMUG's goal is a fast, private, dependable controller for VSITOO mugs. The app
should remain local-first: no account, no cloud dependency, and no Internet
permission for core mug control.

## Approved product direction

Approved August 13, 2026:

- Pixel-first presentation using Material 3 Expressive patterns
- Material You dynamic color from the Pixel wallpaper
- Automatic system light and dark themes
- Warm AMUG colors only as a fallback/accent, not a forced fixed theme
- Fahrenheit by default with a persistent, obvious °F/°C switch
- Physical temperature glow enabled by default and user-disableable

### Approved v1 scope

1. Live mug dashboard
2. Fahrenheit-first unit switching
3. Safe target-temperature and heat controls
4. Temperature-responsive physical LED
5. Fast automatic reconnect to the last mug

### Approved post-v1 scope

1. Drink presets
2. Full lighting controls
3. Target/battery/disconnect notifications
4. Pixel Quick Settings tile
5. Home-screen widget
6. Multiple named mugs
7. Guarded experimental firmware tools
8. Google Home integration

Home Assistant/MQTT is no longer a committed user-facing priority unless demand
emerges; Google Home is the approved consumer integration target.

The detailed 38-feature catalog, safety model, competitive rationale, and
beverage presets are maintained in [PRODUCT_SPEC.md](PRODUCT_SPEC.md).

## Current baseline — v0.1

- Native Kotlin, Jetpack Compose, Material 3
- Android 16 / API 36 target
- Pixel 10 Pro XL installation and startup verified
- Discovers `S6-PLUS-XXXX` without relying on advertised service UUIDs
- Connects to the S6 Plus A300 GATT service
- Enables A302 notifications and serializes A301 writes
- Reads current temperature, target temperature, battery, charging, heating,
  and empty/dry-burn state
- Basic S6 Plus target and heat controls implemented
- Plain S6 parsing and preset controls implemented but not hardware-tested
- Protocol unit tests, Android lint, and debug build pass

## v0.2 alpha implementation

- Serialized BLE callback/state thread with stale-session rejection
- Bounded connect, discovery, notification, write, and readback timeouts
- Three-attempt reconnect and command retry behavior
- Device-status readback required before state-changing commands are confirmed
- Last-mug local reconnect with fresh-state reconciliation
- Complete S6 Plus status/version parsing and Diagnostics event log
- Fahrenheit-first slider, 1°F +/- control, exact numeric entry, and presets
- Empty-state interlock, 140°F warning, and firmware 2/4-hour auto-off controls
- Material You semantic colors and system light/dark behavior
- Custom ambient temperature colors with explicit hold/night-light constraint

This alpha requires renewed physical validation before issues #1, #4, #5, and
#6 can be closed.

## Revision 1 — Hardware-safe controls

Goal: validate each daily-use control against the real S6 Plus without unsafe
or ambiguous behavior.

- Add an in-app diagnostics/event log with TX/RX hex frames
- Show firmware and hardware versions from opcode `02`
- Validate target-temperature writes across the supported 48–66 °C range
- Validate hold/idle behavior and all three S6 Plus gear values
- Separate "heating enabled" from "currently heating" in state and UI
- Verify empty/dry-burn behavior on and off the charger
- Disable controls until initial status is received
- Add command acknowledgement, timeout, and retry reporting
- Capture known-good packet fixtures for tests
- Default the interface to Fahrenheit with a persistent °F/°C switch
- Validate temperature glow: map measured drink temperature from blue through
  amber to red on the S6 Plus RGB LED, show the complete temperature/color
  legend, allow every anchor color to be customized, and persist/reset palettes

Exit criteria: repeated connect/read/write cycles work without the vendor app,
and no control is labeled more confidently than the protocol evidence allows.

## Revision 2 — Connection reliability

Goal: make AMUG reconnect faster and more reliably than the official app.

- Remember the last mug locally by device identity
- Auto-connect when AMUG opens and the mug is nearby
- Add bounded scan, connect, service-discovery, CCCD, and command timeouts
- Retry transient Android GATT failures with fresh GATT instances
- Handle Bluetooth toggles, mug sleep/wake, charger removal, and range loss
- Pause scanning when connected and avoid Android scan throttling
- Add explicit disconnect / forget-device controls
- Preserve UI state across rotation and process recreation

Exit criteria: ten consecutive cold starts connect successfully, and a lost
connection recovers without force-closing the app.

## Revision 3 — Complete S6 Plus experience

Goal: cover useful features without copying the clutter of the vendor app.

- Fahrenheit display and setpoint entry
- Presets for coffee, tea, and custom drinks
- RGB night-light color and on/off control
- Music/light modes with clear previews and an off state
- Hold-light and charge-light settings
- Touch lock and safety-wait settings
- AI self-heating status and control after protocol validation
- Battery voltage and charging-health diagnostics
- Accessible labels, larger text support, and color-contrast review
- Material 3 Expressive review against current Android guidance

Exit criteria: all non-firmware features used by an S6 Plus owner are locally
available, understandable, and tested.

## Revision 4 — Background and automation

Goal: useful automation without unnecessary cloud services.

- Optional connected-device foreground service
- Persistent notification with temperature and heating state
- Quick Settings tile for connect / hold / idle
- Home-screen widget for current and target temperature
- Android automation intents / shortcuts
- Local notifications for target reached, low battery, and empty mug
- Configurable background behavior with explicit battery-impact disclosure

Exit criteria: background features are opt-in, Android-policy compliant, and do
not destabilize foreground BLE control.

## Revision 5 — More devices and integrations

Goal: grow only after the S6 Plus core is dependable.

- Hardware-test plain S6 support
- Add protocol fixtures and capability profiles per VSITOO model
- Google Home integration through an appropriate secure bridge/service design
- Import/export anonymized diagnostics for community debugging
- Signed GitHub releases with reproducible release notes

## Release discipline

Every revision should include:

1. Protocol unit tests for new packet formats.
2. `testDebugUnitTest`, `assembleDebug`, and `lintDebug` passing.
3. Pixel 10 Pro XL cold-launch and reconnect checks.
4. Physical S6 Plus read/write verification for changed controls.
5. No new network permission unless a separate optional feature requires it.
6. Updated protocol and hardware-validation notes.

## Near-term work order

1. Add diagnostics and packet logging.
2. Validate setpoint, gear, hold, and idle commands on the mug.
3. Harden timeout/retry/reconnect behavior.
4. Improve the live status model and labels.
5. Add Fahrenheit and presets.
6. Add lighting controls.
7. Start optional automation only after daily control is reliable.
