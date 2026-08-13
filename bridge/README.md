# AMUG Stationary Bridge

Google Home cannot publish an arbitrary BLE peripheral from an Android app.
Production Google Home integration requires a persistent **stationary** bridge
and a certified Cloud-to-cloud integration. Google policy does not permit a
phone or tablet to be the production intermediary.

This directory defines the future bridge boundary. Core AMUG remains local,
account-free, and Internet-free.

## Safe capability contract

The bridge may expose:

- current and target temperature
- battery, charging, empty, hold enabled, freshness
- explicit off / stop hold
- approved bounded presets after physical validation

The bridge must not initially expose:

- arbitrary heat-on automation
- targets outside validated 120–150°F
- stale state as live state
- ambient/night light as if it can coexist with hold
- firmware update/reset

## Architecture

```text
Google Home -> HTTPS fulfillment -> authenticated stationary bridge -> BLE mug
Android AMUG --------------------------------------^ local provisioning/control
```

Recommended bridge hardware is a small always-on Linux host or Wi-Fi/BLE MCU
within mug range. The Android phone is for setup and daily direct control, not
the relay.

## Reference local API

The future bridge should expose an authenticated LAN API:

- `GET /v1/state`
- `POST /v1/off`
- `POST /v1/preset/{id}`
- `GET /v1/health`

Every write returns pending until fresh BLE readback confirms it. Requests carry
an idempotency key and expiry time; expired heating commands must be rejected.

## Google model

Cloud-to-cloud can accurately model the bridge as a Kettle with `OnOff` and
`TemperatureControl`. Google blocks unattended Kettle `On` automations, which
aligns with AMUG's safety policy. Start with status/off; add approved presets
only after certification and hardware validation.

Official references:

- https://developers.home.google.com/cloud-to-cloud/guides/kettle
- https://developers.home.google.com/cloud-to-cloud/traits/temperaturecontrol
- https://developers.home.google.com/cloud-to-cloud/project/authorization
- https://developers.home.google.com/policies
- https://developers.home.google.com/apis/android/overview
