"""Read-only local AMUG bridge prototype.

This intentionally exposes status only. Heat control remains disabled until the
stationary bridge authentication, expiry, and physical-command validation work
is complete.
"""

import asyncio
import json
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock, Thread

from bleak import BleakClient, BleakScanner

WRITE = "a3010000-0000-0000-0000-000000000000"
NOTIFY = "a3020000-0000-0000-0000-000000000000"
STATE = {"deviceId": "unknown", "observedAt": datetime.now(UTC).isoformat(), "fresh": False, "connected": False, "empty": False, "maintenanceEnabled": False, "currentC": None, "targetC": None, "batteryPercent": None, "charging": False, "pendingCommand": None}
LOCK = Lock()


def parse_status(data: bytes):
    if len(data) < 21 or data[0] != 3:
        return
    flags = data[2]
    with LOCK:
        STATE.update(
            observedAt=datetime.now(UTC).isoformat(), fresh=True, connected=True,
            currentC=data[3] + data[4] / 100, targetC=data[5] + data[6] / 100,
            batteryPercent=data[12] if data[12] <= 100 else None,
            charging=bool(flags & 16), empty=bool(flags & 4), maintenanceEnabled=bool(flags & 1),
        )


async def ble_loop():
    while True:
        device = await BleakScanner.find_device_by_filter(lambda d, a: (a.local_name or "").upper().startswith("S6-PLUS-"), timeout=15)
        if not device:
            await asyncio.sleep(5)
            continue
        try:
            async with BleakClient(device) as client:
                with LOCK: STATE.update(deviceId=device.address, connected=True)
                await client.start_notify(NOTIFY, lambda _, data: parse_status(bytes(data)))
                while client.is_connected:
                    await client.write_gatt_char(WRITE, b"\x03", response=True)
                    await asyncio.sleep(15)
        except Exception:
            with LOCK: STATE.update(connected=False, fresh=False)
            await asyncio.sleep(3)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ("/v1/state", "/v1/health"):
            self.send_error(404); return
        with LOCK: payload = dict(STATE)
        body = json.dumps(payload if self.path == "/v1/state" else {"ok": True, "connected": payload["connected"]}).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    Thread(target=lambda: asyncio.run(ble_loop()), daemon=True).start()
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
