VISTOO S6 MUG REVERSE ENGINEERING GUIDE
========================================

APP INFO:
- Package: com.jimiyoupin.vsitoo
- App: VSITOO (Google Play)
- Device: VSITOO S6 Plus 12oz Smart Self-Heating Coffee Mug
- Features: Temperature control (120-150°F), music LED, Bluetooth, rechargeable

========================================
STEP 1: CAPTURE BLUETOOTH TRAFFIC
========================================

On your Android phone:
1. Enable Developer Options (Settings → About Phone → tap Build 7 times)
2. Enable "Bluetooth HCI snoop log" in Developer Options
3. Toggle Bluetooth off then on to activate logging
4. Use the VSITOO app to:
   - Connect to your S6 mug
   - Change temperature settings
   - Heat/cool the mug
   - View current temperature
5. Disable Bluetooth snoop log

Pull the log via ADB:
  adb bugreport   # generates zip with btsnoop_hci.log
  # OR
  adb pull /sdcard/btsnoop_hci.log .   # pull to computer

========================================
STEP 2: ANALYZE IN WIRESHARK
========================================

1. Open btsnoop_hci.log in Wireshark
2. Filter by device MAC address (find via nRF Connect app):
   bluetooth.addr == AA:BB:CC:DD:EE:FF
3. Filter for GATT ATT packets:
   btatt
4. Look for:
   - Write requests (opcode 0x52) - phone sending data TO mug
   - Notifications (opcode 0x1b) - mug sending data TO phone
5. Note the GATT characteristic UUIDs and handles

========================================
STEP 3: DISCOVER GATT SERVICES (ALTERNATIVE)
========================================

Using nRF Connect Android app:
1. Install nRF Connect from Play Store
2. Connect to your S6 mug
3. "Discover Services" - note all UUIDs
4. Click on each characteristic to read/write
5. Enable notifications on characteristics to see data updates

Common patterns for smart mugs:
- Service UUID: Often custom (e.g., 0xff02, 0x18f0, or uuid starting with 0000...)
- Temperature write characteristic: Usually write-only with 4-byte float or 1-byte command
- Temperature notification: Usually notify property, sends current temp

========================================
STEP 4: DECOMPILE THE APP (IF APK OBTAINED)
========================================

If you get the APK:
1. Install jadx: https://github.com/pxb1988/jadx
2. Open APK in jadx
3. Search for:
   - "Bluetooth" or "GATT" or "Gatt"
   - "temperature" or "temp" or "heat"
   - "connect" or "disconnect"
   - UUID strings like "0000" or "ff02"
4. Look for onClick handlers that send BLE commands

Alternative: apktool for decompiling resources:
  apktool d vsitoo.apk -o output_folder

========================================
STEP 5: ONCE YOU HAVE THE PROTOCOL
========================================

Test with gatttool (Linux) or nRF Connect:
- Write temperature: gatttool -b AA:BB:CC:DD:EE:FF --char-write-req --handle XXXX --value XXXXXXXX
- Read temperature: gatttool -b AA:BB:CC:DD:EE:FF --char-read --handle XXXX

Example command patterns (from similar mugs):
- Set temperature: write [0x01, temp_float_32bit] to characteristic
- Heat on/off: write [0x01] or [0x00] to trigger heating
- Current temp notification: [0xAA, temp_high, temp_low, status]

========================================
STEP 6: BUILD YOUR OWN APP
========================================

Using Python with bluepy or bleak:
```python
from bleak import BleakClient

SERVICE_UUID = "0000xxxx-0000-1000-8000-00805f9b34fb"
CHAR_UUID = "0000xxxx-0000-1000-8000-00805f9b34fb"

async def connect_and_set_temp(mac, temp_c):
    async with BleakClient(mac) as client:
        # Write temperature (example - adjust based on your analysis)
        await client.write_gatt_char(CHAR_UUID, struct.pack('<f', temp_c + 273.15))
```

========================================
QUICK START CHECKLIST
========================================
[] Enable BT HCI snoop log on Android
[] Connect app to S6 mug and interact
[] Pull btsnoop_hci.log via ADB
[] Open in Wireshark, filter by MAC
[] Note characteristic UUIDs and handles
[] (Optional) Decompile APK with jadx
[] Test writes with nRF Connect or gatttool
[] Build custom control app

Need help with any specific step? I can guide you through Wireshark analysis or Python BLE code once you have the packet capture.