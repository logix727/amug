import asyncio

from bleak import BleakClient, BleakScanner

WRITE = "a3010000-0000-0000-0000-000000000000"
NOTIFY = "a3020000-0000-0000-0000-000000000000"


def parse(data: bytes):
    if len(data) < 21 or data[0] != 3:
        return None
    return {
        "empty": bool(data[2] & 4), "wait": data[7],
        "hold_light": data[18], "charge_light": data[19], "night": data[20],
    }


async def status(client, queue, attempts=3):
    for _ in range(attempts):
        await client.write_gatt_char(WRITE, b"\x03", response=True)
        try:
            while True:
                parsed = parse(await asyncio.wait_for(queue.get(), 3))
                if parsed:
                    return parsed
        except TimeoutError:
            await asyncio.sleep(.5)
    raise TimeoutError("No status response")


async def write_and_confirm(client, queue, command, field, expected, label):
    print(f"TX {command.hex().upper()}  {label}")
    await client.write_gatt_char(WRITE, command, response=True)
    result = await status(client, queue)
    print(f"Readback {field}={result[field]}")
    if result[field] != expected:
        raise RuntimeError(f"{label} not confirmed")


async def main():
    device = await BleakScanner.find_device_by_filter(lambda _, a: (a.local_name or "").upper().startswith("S6-PLUS-"), timeout=20)
    if not device:
        raise SystemExit("S6 Plus not found")
    queue = asyncio.Queue()
    async with BleakClient(device, timeout=15) as client:
        await client.start_notify(NOTIFY, lambda _, data: queue.put_nowait(bytes(data)))
        await asyncio.sleep(.75)
        original = await status(client, queue)
        if original["empty"]:
            raise SystemExit("Mug reports empty; refusing settings validation")
        print(f"Baseline: {original}")

        alternate_wait = 4 if original["wait"] != 4 else 2
        await write_and_confirm(client, queue, bytes((5, alternate_wait)), "wait", alternate_wait, f"auto-off {alternate_wait}h")
        await write_and_confirm(client, queue, bytes((5, original["wait"])), "wait", original["wait"], "restore auto-off")

        for opcode, field in ((0x0B, "hold_light"), (0x0C, "charge_light")):
            original_value = original[field]
            alternate = 0 if original_value else 1
            await write_and_confirm(client, queue, bytes((opcode, alternate)), field, alternate, f"toggle {field}")
            await write_and_confirm(client, queue, bytes((opcode, original_value)), field, original_value, f"restore {field}")

        await client.stop_notify(NOTIFY)
        print("PASS: auto-off and indicator-light writes/readback/restoration confirmed")


if __name__ == "__main__":
    asyncio.run(main())
