# AMUG Product Specification

## Product promise

AMUG is the dependable, private controller for VSITOO smart mugs:

- no account or cloud dependency for mug control
- no Internet permission in the core app
- accurate state instead of optimistic UI
- explicit safety behavior instead of vague "AI" labels
- Pixel-quality Android design

## Competitive position

- **Copy from Ember:** precise 1°F control, current and target temperature,
  presets, notifications, device names, unit switching, and clear LED states.
- **Copy from Nextmug:** obvious presets, physical-state feedback, empty
  detection, and usability without an account or phone.
- **Use VSITOO hardware:** 120–150°F range, liquid/dry-boil sensing, charging
  state, RGB lighting, and local BLE control.
- **Avoid:** mandatory login, stale telemetry, silent disconnects, cloud-loaded
  controls, forced updates, and decorative clutter on the daily control screen.

## Temperature presets

These are drinking/holding temperatures, not brewing temperatures. Presets are
editable and stored locally.

| Preset | Default | Intended use |
|---|---:|---|
| Green tea | 125°F / 52°C | Green and delicate white tea |
| White tea | 125°F / 52°C | Delicate white tea |
| Oolong | 130°F / 54°C | Oolong and cooler tea |
| Cocoa | 130°F / 54°C | Hot chocolate / cocoa |
| Coffee | 135°F / 57°C | Drip, pour-over, and general coffee |
| Espresso | 135°F / 57°C | Espresso and Americano |
| Latte | 135°F / 57°C | Latte and cappuccino |
| Black tea | 135°F / 57°C | Black tea |
| Herbal tea | 135°F / 57°C | Herbal infusions |
| Hot | 140°F / 60°C | Explicit user choice; show "sip carefully" |

Custom values remain available from 120–150°F. AMUG shows a persistent caution
at 140°F and above. It must not include a baby-formula preset: formula requires
a separate controlled preparation and cooling process.

## Safety model

1. Mug-reported `empty` / dry-boil state is authoritative.
2. AMUG confirms safety-relevant writes through status readback.
3. Controls are disabled until an initial status snapshot arrives.
4. The UI distinguishes **heat enabled**, **actively heating**, **holding**,
   **empty**, **charging**, and **disconnected**.
5. Phone-only schedules and timers are labeled best-effort. They are guaranteed
   only when stored or enforced by mug firmware.
6. AMUG never silently restores heating after an empty event, update, or
   unexpected reconnect.
7. Firmware update/reset commands stay out of normal builds until integrity,
   rollback, and recovery behavior are validated.

## Approved feature catalog

### P0 — Dependable v1

1. **Live dashboard:** current/target temperature, delta, battery, charger,
   empty, heat-enabled, actively-heating, holding, and stale-data timestamp.
2. **Fahrenheit-first units:** °F default; persistent, obvious °F/°C switch.
3. **Exact setpoint control:** slider plus large +/- and numeric entry.
4. **Verified writes:** pending/confirmed/failed states from target readback.
5. **Atomic connection snapshot:** read supported state before enabling controls.
6. **Fast reconnect:** remember the last mug and reconcile actual state on open.
7. **Reliable GATT queue:** stage timeouts, bounded retry, fresh GATT after
   transport failure, and superseded slider commands.
8. **Empty-state interlock:** disable heat actions when firmware reports empty.
9. **Contradictory-state guard:** suspend automations on impossible state combos.
10. **Temperature glow:** physical LED maps measured temperature blue → amber →
    red; optional, persistent, and overridden by safety states.
11. **Drink presets:** editable tea, cocoa, coffee, espresso, latte, and custom.
12. **Hot-temperature caution:** warning at 140°F+ and app safety ceiling.
13. **Ready band:** stable target tolerance before declaring the drink ready.
14. **Ready feedback:** in-app state, optional haptic, and LED cue.
15. **Connection clarity:** initializing, reconnecting, permission, asleep, and
    out-of-range states.
16. **Local device identity:** friendly mug name, forget, one active connection.
17. **Diagnostics snapshot:** firmware, Android, permissions, GATT, RSSI, result.
18. **Bounded BLE log:** redacted TX/RX, callbacks, retry, disconnect, export.
19. **Accessibility:** TalkBack, large type, switch access, non-color-only state,
    and alternatives to slider-only control.
20. **Material You UI:** Pixel dynamic color, system light/dark, edge-to-edge,
    predictive back, semantic haptics, and adaptive layouts.

### P1 — Revision 2

21. **Sleep/hold timer:** 15/30/60/120 minutes; firmware-enforced where proven,
    otherwise visibly best-effort.
22. **Unattended hold limit:** expose and validate VSITOO safety-wait semantics.
23. **Lighting studio:** RGB, night light, hold/charge lights, music modes, and
    restoring temperature glow.
24. **Safety LED priority:** empty, abnormal heat, low battery, and update states
    override decorative lighting with accessible cues.
25. **Notifications:** ready, low battery, empty, disconnect, and unusually hot.
26. **Quick Settings tile:** safe connect/open/hold-idle; require unlock for
    actions that can increase heat.
27. **Home-screen widget:** cached state and freshness time; tap to reconnect.
28. **Multiple mugs:** names, per-mug presets, switching, and honest connection
    limits until simultaneous BLE is proven.

### P2 — Later integrations

29. **Google Home integration:** secure bridge/service for status and approved
    presets; no arbitrary unsafe temperature control by default.
30. **Local session history:** temperature, battery, heating, empty, and
    connection events with retention controls.
31. **Local schedules:** presets/quiet lighting with missed-action expiry.
32. **Automation surface:** authenticated, clamped Android shortcuts/intents.
33. **Support bundle:** user-previewed, redacted diagnostics and problem note.
34. **Guided self-test:** safe sensor, characteristic, readback, and LED tests.

### Experimental / gated

35. **Firmware compatibility matrix:** conservative behavior on unknown versions.
36. **Firmware tools:** preflight, integrity, rollback, and recovery research.
37. **Steep-then-cool routine:** only after timer/background guarantees are known.
38. **Calibration notes:** reference comparisons without changing safety limits.

## Pixel / Android implementation rules

- Stable Material 3 is the production dependency. Expression comes from type,
  shape, hierarchy, and meaningful motion, not alpha-only components.
- Dynamic color uses semantic roles; fixed AMUG colors are fallback branding.
- Compact below 600dp; adaptive/two-pane at 600dp and 840dp.
- Foreground-only BLE for v1. Background is opt-in later through a compliant
  connected-device service or companion-device APIs.
- No periodic scans, exact-alarm abuse, battery-optimization prompt, or silent
  always-on reconnect.
- Quick Settings/widget data discloses freshness and never implies cached data
  is live.

## Sources

- Ember Mug 2: https://ember.com/products/ember-mug-2
- Ember Android app: https://play.google.com/store/apps/details?id=com.embertech
- Nextmug FAQ: https://nextmug.com/pages/frequently-asked-questions
- VSITOO S6 Plus: https://www.vsitoo.com/products/s6-plus-smart-mug
- SCA coffee standards: https://sca.coffee/research/coffee-standards
- UK Tea & Infusions Association: https://www.tea.co.uk/make-a-perfect-brew
- IARC very-hot beverages: https://www.iarc.who.int/wp-content/uploads/2018/07/pr244_E.pdf
- Android Material 3: https://developer.android.com/develop/ui/compose/designsystems/material3
- Android BLE background: https://developer.android.com/develop/connectivity/bluetooth/ble/background
