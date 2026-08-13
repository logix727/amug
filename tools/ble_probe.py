import argparse
import asyncio
from datetime import datetime

from bleak import BleakClient, BleakScanner

SERVICE = "a3000000-0000-0000-0000-000000000000"
WRITE = "a3010000-0000-0000-0000-000000000000"
NOTIFY = "a3020000-0000-0000-0000-000000000000"


def parse_status(data: bytes) -> str:
    if len(data) < 21 or data[0] != 0x03:
        return f"raw={data.hex().upper()}"
    flags = data[2]
    current = data[3] + data[4] / 100
    target = data[5] + data[6] / 100
    rgb = int.from_bytes(data[8:11])
    return (
        f"status current={current:.2f}C/{current * 9 / 5 + 32:.1f}F "
        f"target={target:.2f}C/{target * 9 / 5 + 32:.1f}F "
        f"hold={bool(flags & 1)} empty={bool(flags & 4)} charging={bool(flags & 16)} "
        f"wait={data[7]}h rgb=#{rgb:06X} mode={data[11]} battery={data[12]}% "
        f"battery_mv={int.from_bytes(data[16:18])} night_light={data[20] == 1}"
    )


async def find_mug(timeout: float):
    print(f"Scanning for S6 Plus for {timeout:.0f}s...")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    for device, advertisement in devices.values():
        name = advertisement.local_name or device.name or ""
        if name.upper().startswith("S6-PLUS-") or SERVICE in [s.lower() for s in advertisement.service_uuids]:
            print(f"Found {name} [{device.address}] RSSI={advertisement.rssi}")
            return device
    return None


async def main():
    parser = argparse.ArgumentParser(description="Read-only VSITOO S6 Plus BLE probe")
    parser.add_argument("--timeout", type=float, default=12)
    parser.add_argument("--listen", type=float, default=6)
    args = parser.parse_args()

    device = await find_mug(args.timeout)
    if not device:
        raise SystemExit("S6 Plus not found. Wake the mug and ensure the phone app is disconnected.")

    packets = []

    def notify(_, data: bytearray):
        packet = bytes(data)
        packets.append(packet)
        stamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        print(f"{stamp} RX {packet.hex().upper()}  {parse_status(packet)}")

    async with BleakClient(device, timeout=15) as client:
        print(f"Connected={client.is_connected}")
        services = client.services
        for service in services:
            if "a300" in service.uuid.lower():
                print(f"Service {service.uuid}")
                for characteristic in service.characteristics:
                    print(f"  {characteristic.uuid} properties={characteristic.properties}")

        await client.start_notify(NOTIFY, notify)
        for label, command in (("version", b"\x02"), ("status", b"\x03")):
            print(f"TX {command.hex().upper()}  {label}")
            await client.write_gatt_char(WRITE, command, response=True)
            await asyncio.sleep(1)
        await asyncio.sleep(args.listen)
        await client.stop_notify(NOTIFY)

    if not packets:
        raise SystemExit("Connected but received no notifications")


if __name__ == "__main__":
    asyncio.run(main())
