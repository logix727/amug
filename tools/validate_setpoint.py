import asyncio
from dataclasses import dataclass

from bleak import BleakClient, BleakScanner

WRITE = "a3010000-0000-0000-0000-000000000000"
NOTIFY = "a3020000-0000-0000-0000-000000000000"


@dataclass
class Status:
    current_c: float
    target_c: float
    flags: int
    empty: bool


def parse(data: bytes):
    if len(data) < 21 or data[0] != 3:
        return None
    return Status(
        current_c=data[3] + data[4] / 100,
        target_c=data[5] + data[6] / 100,
        flags=data[2],
        empty=bool(data[2] & 4),
    )


def command(celsius: float):
    hundredths = round(celsius * 100)
    return bytes((4, hundredths // 100, hundredths % 100))


async def wait_status(client, queue, timeout=3, attempts=3):
    for attempt in range(1, attempts + 1):
        await client.write_gatt_char(WRITE, b"\x03", response=True)
        deadline = asyncio.get_running_loop().time() + timeout
        while asyncio.get_running_loop().time() < deadline:
            try:
                data = await asyncio.wait_for(queue.get(), deadline - asyncio.get_running_loop().time())
            except TimeoutError:
                break
            status = parse(data)
            if status:
                return status
        print(f"Status attempt {attempt}/{attempts} timed out")
        await asyncio.sleep(.5)
    raise TimeoutError("Mug did not return status")


async def main():
    device = await BleakScanner.find_device_by_filter(
        lambda _, advertisement: (advertisement.local_name or "").upper().startswith("S6-PLUS-"),
        timeout=20,
    )
    if not device:
        raise SystemExit("S6 Plus not found")
    queue = asyncio.Queue()
    async with BleakClient(device, timeout=15) as client:
        await client.start_notify(NOTIFY, lambda _, data: queue.put_nowait(bytes(data)))
        await asyncio.sleep(.75)
        await client.write_gatt_char(WRITE, b"\x02", response=True)
        await asyncio.sleep(.75)
        original = await wait_status(client, queue)
        if original.empty:
            raise SystemExit("Mug reports empty; refusing setpoint validation")
        test_target = 55.0 if abs(original.target_c - 55.0) > .02 else 54.44
        print(f"Baseline: current={original.current_c:.2f}C target={original.target_c:.2f}C")
        print(f"Setting reversible test target: {test_target:.2f}C / {test_target * 9 / 5 + 32:.1f}F")
        await client.write_gatt_char(WRITE, command(test_target), response=True)
        changed = await wait_status(client, queue)
        print(f"Readback: current={changed.current_c:.2f}C target={changed.target_c:.2f}C")
        if abs(changed.target_c - test_target) > .02:
            raise SystemExit("Setpoint change was not confirmed")
        print(f"Restoring original target: {original.target_c:.2f}C / {original.target_c * 9 / 5 + 32:.1f}F")
        await client.write_gatt_char(WRITE, command(original.target_c), response=True)
        restored = await wait_status(client, queue)
        print(f"Restored: current={restored.current_c:.2f}C target={restored.target_c:.2f}C")
        if abs(restored.target_c - original.target_c) > .02:
            raise SystemExit("Original setpoint was not restored")
        await client.stop_notify(NOTIFY)
        print("PASS: setpoint write/readback and restoration confirmed")


if __name__ == "__main__":
    asyncio.run(main())
