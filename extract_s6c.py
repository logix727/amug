import re

data = open(r'C:\dev\mug\decompiled_pixel\resources\assets\apps\__UNI__9E8AA0A\www\app-service.js', encoding='utf-8', errors='replace').read()

# Look for the device models map - the S6 (non Plus) key
# In the models map we saw: S6:{NAME_REGS:"vsitooS6",addDevice:...
# Find that with more context - the temp control command map
print('=== Search for temp/set command builders ===')
for kw in ['setTemper', 'setTemperature', 'tempCMD', 'temperatureCMD', 'sendTemperature']:
    idxs = [m.start() for m in re.finditer(re.escape(kw), data)]
    print(f'{kw}: {len(idxs)} matches at {idxs[:15]}')
    for i in idxs[:2]:
        s = max(0, i-150); e = min(len(data), i+400)
        print(f'  ...{data[s:e]}...')
    print()