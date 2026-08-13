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

# The apkcombo download page
url = "https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/download/apk"
html = fetch(url)

# Look for version selectors / package info
print("=== Looking for version/arch info ===")
versions = re.findall(r'href="([^"]*version/[^"]*)"', html)
for v in versions[:10]:
    print(f"  {v[:180]}")

print("\n=== Looking for download POST endpoints ===")
for pat in [r'action="([^"]+)"', r'form[^>]*action="([^"]+)"', r'data-url="([^"]+)"']:
    m = re.findall(pat, html)
    if m:
        for x in m[:8]:
            print(f"  {x[:180]}")

print("\n=== Looking for .apk URLs anywhere ===")
apks = re.findall(r'https?://[^"\'\s<>]+\.apk[^"\'\s<>]*', html)
for a in apks[:10]:
    print(f"  {a[:200]}")

# The download page usually has a JSON blob or redirect with the version
print("\n=== Looking for download link patterns ===")
for pat in [r'"url"\s*:\s*"([^"]+)"', r'window\.location[^;]*=[^;]*"([^"]+)"', r'id="dl_[^"]*"[^>]*href="([^"]+)"']:
    m = re.findall(pat, html)
    if m:
        for x in m[:10]:
            print(f"  {x[:200]}")