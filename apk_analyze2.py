import urllib.request
import ssl
import re
import time

ssl._create_default_https_context = ssl._create_unverified_context

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=25) as response:
        return response.read().decode('utf-8', errors='replace')

url = "https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/download/apk"
html = fetch(url)

# Save full HTML for analysis
with open("apkcombo_page.html", "w", encoding="utf-8") as f:
    f.write(html)

print(f"Saved {len(html)} bytes to apkcombo_page.html")

# Look for version numbers and options
print("\n=== Version info ===")
for pat in [r'versionName[^,}]*', r'data-version="([^"]+)"', r'option[^>]*value="([^"]*version[^"]*)"',
            r'(\d+\.\d+\.\d+[^"<]*)', r'ver\s*=\s*["\']([^"\']+)["\']']:
    m = re.findall(pat, html)
    if m:
        print(f"Pattern: {pat[:30]}")
        for x in m[:12]:
            print(f"  {x[:120]}")

print("\n=== All apkcombo links ===")
links = re.findall(r'href="(https?://apkcombo\.com[^"]*)"', html)
seen = set()
for l in links:
    if l not in seen:
        seen.add(l)
        print(f"  {l[:160]}")