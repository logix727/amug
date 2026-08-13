import re

data = open(r'C:\dev\mug\decompiled_pixel\resources\assets\apps\__UNI__9E8AA0A\www\app-service.js', encoding='utf-8', errors='replace').read()

# The models map - search for the S6 model class definitions
# Look for the device-specific control model, find "S6" in the model map area
print('=== Looking for S6 model class (device control) ===')

# Find all occurrences of the protocol send command area
# The S6 (non-Plus) model def may use key 'S6' (no quotes)
for m in re.finditer(r'[S6:"VSITOO S6"]+SERVICE_UUID', data):
    start = max(0, m.start() - 100)
    end = min(len(data), m.end() + 200)
    print(f'Found at {m.start()}:')
    print(data[start:end])
    print('---')

# Also search for model key patterns
print('\n=== Search for device model config for S6 ===')
for m in re.finditer(r'"S6":\{SERVICE_UUID', data):
    start = m.start()
    end = min(len(data), start + 1800)
    print(f'Found at {start}:')
    print(data[start:end])
    print('---')