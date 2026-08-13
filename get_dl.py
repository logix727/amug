import urllib.request
import ssl
import re
import time

ssl._create_default_https_context = ssl._create_unverified_context

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

# Step 1: POST to get the app details with download links
url = "https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/01a1200x20240308/dl"
data = b"package_name=com.jimiyoupin.vsitoo&version="

req = urllib.request.Request(url, data=data, headers={
    'User-Agent': UA,
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
    'X-Requested-With': 'XMLHttpRequest',
    'Referer': 'https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/download/apk',
})

try:
    with urllib.request.urlopen(req, timeout=30) as response:
        html = response.read().decode('utf-8', errors='replace')
        print(f"Got {len(html)} bytes")
        
        # Save for analysis
        with open("apkcombo_dl.html", "w", encoding="utf-8") as f:
            f.write(html)
        
        # Find variant download links
        print("\n=== Variant links (download URLs) ===")
        variants = re.findall(r'href="([^"]*\.apk[^"]*)"', html)
        seen = set()
        for v in variants:
            if v not in seen:
                seen.add(v)
                print(f"  {v[:200]}")
        
        # Find all download-ish links
        print("\n=== All hrefs with 'download' ===")
        dls = re.findall(r'href="([^"]*download[^"]*)"', html, re.IGNORECASE)
        seen2 = set()
        for d in dls:
            if d not in seen2:
                seen2.add(d)
                print(f"  {d[:200]}")
                
except Exception as e:
    print(f"Error: {e}")