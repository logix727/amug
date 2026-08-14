import asyncio

from bleak import BleakClient, BleakScanner

WRITE = "a3010000-0000-0000-0000-000000000000"
NOTIFY = "a3020000-0000-0000-0000-000000000000"


def parse(data: bytes):
    if len(data) < 21 or data[0] != 3:
        return None
    return {"hold": bool(data[2] & 1), "empty": bool(data[2] & 4), "rgb": bytes(data[8:11]), "mode": data[11], "night": data[20] == 1}


async def read_status(client, queue):
    for _ in range(3):
        await client.write_gatt_char(WRITE, b"\x03", response=True)
        try:
            while True:
                value = parse(await asyncio.wait_for(queue.get(), 3))
                if value:
                    return value
        except TimeoutError:
            await asyncio.sleep(.5)
    raise TimeoutError("No status")


async def confirmed(client, queue, command, predicate, label):
    print(f"TX {command.hex().upper()}  {label}")
    await client.write_gatt_char(WRITE, command, response=True)
    value = await read_status(client, queue)
    print(f"Readback {value}")
    if not predicate(value):
        raise RuntimeError(f"Not confirmed: {label}")
    return value


async def main():
    device = await BleakScanner.find_device_by_filter(lambda _, a: (a.local_name or "").upper().startswith("S6-PLUS-"), timeout=20)
    if not device:
        raise SystemExit("S6 Plus not found; release Android BLE first")
    queue = asyncio.Queue()
    async with BleakClient(device, timeout=15) as client:
        await client.start_notify(NOTIFY, lambda _, data: queue.put_nowait(bytes(data)))
        await asyncio.sleep(.75)
        original = await read_status(client, queue)
        if original["empty"]:
            raise SystemExit("Mug reports empty")
        print(f"Baseline {original}")

        if original["night"]:
            await confirmed(client, queue, b"\x07\x00\x00\x00\x00", lambda s: not s["night"], "night off")
        if not original["hold"]:
            await confirmed(client, queue, b"\x06\x01", lambda s: s["hold"], "hold on for music")

        for mode in range(6):
            await confirmed(client, queue, bytes((9, mode)), lambda s, mode=mode: s["mode"] == mode and s["hold"] and not s["night"], f"music mode {mode + 1}")
        await confirmed(client, queue, b"\x09\x16", lambda s: s["mode"] == 0x16, "music off")

        await confirmed(client, queue, b"\x06\x00", lambda s: not s["hold"], "hold off before ambient")
        test_rgb = bytes.fromhex("2468AC")
        await confirmed(client, queue, b"\x07" + test_rgb + b"\x01", lambda s: s["night"] and not s["hold"] and s["rgb"] == test_rgb, "ambient on")
        await confirmed(client, queue, b"\x07\x00\x00\x00\x00", lambda s: not s["night"], "ambient off")

        if original["mode"] in range(6):
            await confirmed(client, queue, bytes((9, original["mode"])), lambda s: s["mode"] == original["mode"], "restore music")
        else:
            await confirmed(client, queue, b"\x09\x16", lambda s: s["mode"] == 0x16, "restore music off")
        await confirmed(client, queue, bytes((6, 1 if original["hold"] else 0)), lambda s: s["hold"] == original["hold"], "restore hold")
        if original["night"]:
            await confirmed(client, queue, b"\x07" + original["rgb"] + b"\x01", lambda s: s["night"], "restore night light")
        await client.stop_notify(NOTIFY)
        print("PASS: six music modes, off, ambient exclusion, and original state restoration confirmed")


if __name__ == "__main__":
    asyncio.run(main())
