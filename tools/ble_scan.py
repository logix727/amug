import asyncio

from bleak import BleakScanner


async def main():
    devices = await BleakScanner.discover(timeout=10, return_adv=True)
    for device, advertisement in sorted(
        devices.values(), key=lambda item: item[1].rssi, reverse=True
    ):
        name = advertisement.local_name or device.name or "unnamed"
        print(
            f"{name:32} | {device.address} | {advertisement.rssi:4} dBm | "
            f"{advertisement.service_uuids}"
        )


if __name__ == "__main__":
    asyncio.run(main())
