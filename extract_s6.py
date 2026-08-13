import re

data = open(r'C:\dev\mug\decompiled_pixel\resources\assets\apps\__UNI__9E8AA0A\www\app-service.js', encoding='utf-8', errors='replace').read()

# Find the model definitions for S6
print('=== Looking for VSITOO S6 model def ===')
for m in re.finditer(r'"VSITOO S6":\{', data):
    start = m.start()
    end = min(len(data), start + 2500)
    print(f'Found at {start}:')
    print(data[start:end])
    print('\n' + '='*80 + '\n')

print('=== Looking for VSITOO S6 Plus model def ===')
for m in re.finditer(r'"VSITOO S6 Plus":\{', data):
    start = m.start()
    end = min(len(data), start + 2500)
    print(f'Found at {start}:')
    print(data[start:end])
    print('\n' + '='*80 + '\n')