import urllib.request
import ssl
import re
import json
import time

ssl._create_default_https_context = ssl._create_unverified_context

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

def fetch(url, max_retries=2):
    for i in range(max_retries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'text/html,application/xhtml+xml'})
            with urllib.request.urlopen(req, timeout=25) as response:
                return response.read().decode('utf-8', errors='replace')
        except Exception as e:
            if i == max_retries - 1:
                raise
            time.sleep(2)

# Try apkcombo - usually programmatically accessible
sources = [
    "https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/",
    "https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/download/apk",
    "https://apk.support/download-app/com.jimiyoupin.vsitoo",
    "https://androiddownloadapk.com/com.jimiyoupin.vsitoo",
]

for url in sources:
    print(f"\n=== {url}")
    try:
        html = fetch(url)
        print(f"Fetched {len(html)} bytes")
        
        # Find direct APK download links
        apk_links = re.findall(r'href="(https?://[^"]*\.apk[^"]*)"', html, re.IGNORECASE)
        print(f"APK links: {len(apk_links)}")
        for l in apk_links[:8]:
            print(f"  {l[:200]}")
        
        # Look for download buttons / variations
        for pat in [r'(https?://[^"\']*download[^"\']*)', r'data-url="([^"]+)"', r'id="download-button" href="([^"]+)"']:
            m = re.findall(pat, html, re.IGNORECASE)
            if m:
                print(f"  Pattern {pat[:20]}: {[x[:150] for x in m[:5]]}")
    except Exception as e:
        print(f"Error: {str(e)[:150]}")
    time.sleep(1)