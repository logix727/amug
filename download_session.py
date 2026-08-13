import urllib.request
import urllib.parse
import http.cookiejar
import ssl
import re
import os
import time

ssl._create_default_https_context = ssl._create_unverified_context

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

# Set up cookie jar and opener
cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
opener.addheaders = [
    ('User-Agent', UA),
    ('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'),
    ('Accept-Language', 'en-US,en;q=0.9'),
]

# 1. First visit apkpure.com to establish cookies
print("=== Step 1: Establishing session with apkpure.com ===")
try:
    resp = opener.open("https://apkpure.com/", timeout=30)
    print(f"apkpure.com status: {resp.status}")
    html = resp.read().decode('utf-8', errors='replace')
    print(f"Cookies so far: {[c.name for c in cj]}")
except Exception as e:
    print(f"apkpure session error: {e}")

# 2. Get checkin token from apkcombo
print("\n=== Step 2: Get checkin token ===")
try:
    req = urllib.request.Request("https://apkcombo.com/checkin", data=b"", headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=30) as response:
        token = response.read().decode('utf-8', errors='replace')
        print(f"Token: {token[:200]}")
except Exception as e:
    print(f"Checkin error: {e}")
    token = "error=403"

# 3. Build download URL and follow redirects with cookie jar
variant_href = "https://apkcombo.com/d?u=aHR0cHM6Ly9kb3dubG9hZC5wdXJlYXBrLmNvbS9iL1hBUEsvWTI5dExtcHBiV2w1YjNWd2FXNHVkbk5wZEc5dlh6RTBNVjh4TTJRNFlqQmpOQT9hczI9NjQ4YTE2YzBkM2UyN2YzZDhjMjZiMWE3YWUxYTI4M2Y2YzU4NjI4YyZrPWE5YjNiMjdjZmI5NTMxNDRlNjcyNmM0ZWRhMmQwZDZhNmM1ODYyOGMmX3A9WTI5dExtcHBiV2w1YjNWd2FXNHVkbk5wZEc5diZjPTElN0NIRUFMVEhfQU5EX0ZJVE5FU1MlN0NiMmxrUFRrbVpHVjJQVlpUU1ZSUFR5WjBQWGhoY0dzbWN6MHpOalEyTkRVNU9DWjJiajB4TGpBdU1UUXhKblpqUFRFME1RJl9mbj1WbE5KVkU5UFh6RXVNQzR4TkRGZllYQnJZMjl0WW04dVkyOXRMbmhoY0dzJTNE"
download_url = variant_href + "&" + token + "&package_name=com.jimiyoupin.vsitoo&lang=en"

print("\n=== Step 3: Downloading via apkcombo /d ===")
try:
    resp = opener.open(download_url, timeout=120)
    print(f"Status: {resp.status}")
    print(f"Final URL: {resp.geturl()}")
    
    # If we get the HTML redirect page, follow the meta refresh
    data = resp.read()
    print(f"Downloaded {len(data)} bytes")
    
    if data[:2] == b'PK':
        with open("VSITOO_1.0.141.xapk", "wb") as f:
            f.write(data)
        print("SUCCESS: Got the XAPK!")
    else:
        # Check for meta refresh URL
        m = re.search(r'url=([^"\']+)', data.decode('utf-8', errors='replace'))
        if m:
            redirect_target = m.group(1)
            print(f"Meta refresh to: {redirect_target[:200]}")
            # Follow it
            print("Following redirect...")
            resp2 = opener.open(redirect_target, timeout=120)
            data2 = resp2.read()
            print(f"Downloaded {len(data2)} bytes")
            print(f"Final URL: {resp2.geturl()}")
            if data2[:2] == b'PK':
                with open("VSITOO_1.0.141.xapk", "wb") as f:
                    f.write(data2)
                print("SUCCESS: Got the XAPK!")
            else:
                print(data2[:500].decode('utf-8', errors='replace'))
        else:
            print(data[:500].decode('utf-8', errors='replace'))
except Exception as e:
    print(f"Download error: {e}")