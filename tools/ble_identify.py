import asyncio

from bleak import BleakClient, BleakScanner


async def inspect(device, name, rssi):
    try:
        async with BleakClient(device, timeout=7) as client:
            services = [service.uuid.lower() for service in client.services]
            print(f"{name:18} {device.address} {rssi:4} dBm -> {services}")
            if any("a300" in service for service in services):
                print(f"FOUND VSITOO PROFILE: {device.address}")
                return device
    except Exception as error:
        print(f"{name:18} {device.address} {rssi:4} dBm -> {type(error).__name__}: {error}")
    return None


async def main():
    discovered = await BleakScanner.discover(timeout=12, return_adv=True)
    candidates = sorted(discovered.values(), key=lambda item: item[1].rssi, reverse=True)
    candidates = [item for item in candidates if item[1].rssi >= -70 and not (item[1].local_name or item[0].name)]
    print(f"Inspecting {len(candidates)} nearby unnamed BLE devices (service discovery only)...")
    for device, advertisement in candidates:
        found = await inspect(device, "unnamed", advertisement.rssi)
        if found:
            return
    raise SystemExit("No nearby unnamed device exposed the A300 service")


if __name__ == "__main__":
    asyncio.run(main())
