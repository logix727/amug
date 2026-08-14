# Alpha 2 ANR Incident

## User-visible failure

AMUG alpha 2 appeared frozen and Android reported the app as not responding.
Temperature controls were therefore unusable even though direct Windows BLE
validation showed the mug and protocol were healthy.

## Evidence

Pixel 10 Pro XL logs showed:

- input dispatch timeout after 5 seconds
- service execution timeout after 20 seconds
- one service execution stuck for approximately 200 seconds
- Android killed `dev.logix.amug` for a background ANR

## Root cause

Alpha 2 performed nested synchronous Room, DataStore, migration, and Glance
widget operations using `runBlocking` from Application/service/tile startup.
Those disk-backed operations blocked the Android main thread and service
lifecycle callbacks. The BLE protocol was not the source of the freeze.

## Corrective action in alpha 3

- removed all `runBlocking` calls from production Android code
- moved repository migration/pruning to `Dispatchers.IO`
- made selected-mug/widget/tile snapshot reads suspending
- moved tile work into a lifecycle-owned coroutine scope
- deferred connect-last until asynchronous repository initialization completes
- preserved one foreground-service BLE owner

## Resolution validation

Alpha 3 launched in 494 ms and remained responsive through idle, scan, connect,
notification setup, version/status reads, `0601` hold enable, command readback,
dashboard scrolling, and refresh. Android reported no input-dispatch or service
execution ANR. The mug warmed from roughly 104°F to 113°F toward its 130°F
target after hold was enabled.

## Regression gate

Alpha 3 must pass:

1. clean unit tests, APK assembly, and Android lint
2. repeated cold launch without input/service ANR
3. bound idle service startup without main-thread blocking
4. connect-last foreground service startup
5. live telemetry and reversible 130°F → 131°F → 130°F command readback
