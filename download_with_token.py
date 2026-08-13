import urllib.request
import ssl
import re
import os
import time
import base64

ssl._create_default_https_context = ssl._create_unverified_context

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

# 1. Get checkin token
print("=== Step 1: Get checkin token ===")
try:
    req = urllib.request.Request("https://apkcombo.com/checkin", data=b"", headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=30) as response:
        token = response.read().decode('utf-8', errors='replace')
        print(f"Token: {token[:200]}")
except Exception as e:
    print(f"Checkin error: {e}")
    token = "error=403"
    print("Falling back to error token")

# 2. Build download URL with token
variant_href = "https://apkcombo.com/d?u=aHR0cHM6Ly9kb3dubG9hZC5wdXJlYXBrLmNvbS9iL1hBUEsvWTI5dExtcHBiV2w1YjNWd2FXNHVkbk5wZEc5dlh6RTBNVjh4TTJRNFlqQmpOQT9hczI9NjQ4YTE2YzBkM2UyN2YzZDhjMjZiMWE3YWUxYTI4M2Y2YzU4NjI4YyZrPWE5YjNiMjdjZmI5NTMxNDRlNjcyNmM0ZWRhMmQwZDZhNmM1ODYyOGMmX3A9WTI5dExtcHBiV2w1YjNWd2FXNHVkbk5wZEc5diZjPTElN0NIRUFMVEhfQU5EX0ZJVE5FU1MlN0NiMmxrUFRrbVpHVjJQVlpUU1ZSUFR5WjBQWGhoY0dzbWN6MHpOalEyTkRVNU9DWjJiajB4TGpBdU1UUXhKblpqUFRFME1RJl9mbj1WbE5KVkU5UFh6RXVNQzR4TkRGZllYQnJZMjl0WW04dVkyOXRMbmhoY0dzJTNE"
download_url = variant_href + "&" + token + "&package_name=com.jimiyoupin.vsitoo&lang=en"
print(f"\n=== Download URL ===")
print(download_url[:200])
print("...")

# 3. Download the file (follow redirects)
print("\n=== Step 2: Downloading ===")
try:
    req = urllib.request.Request(download_url, headers={'User-Agent': UA, 'Referer': 'https://apkcombo.com/vsitoo/com.jimiyoupin.vsitoo/download/apk'})
    with urllib.request.urlopen(req, timeout=120) as response:
        final_url = response.geturl()
        print(f"Final URL: {final_url}")
        print(f"Status: {response.status}")
        data = response.read()
        print(f"Downloaded {len(data)} bytes")
        
        filename = "VSITOO_1.0.141.xapk"
        with open(filename, "wb") as f:
            f.write(data)
        print(f"Saved to {os.path.abspath(filename)}")
        
        # Check if it's a zip
        if data[:2] == b'PK':
            print("SUCCESS: It's a ZIP/XAPK file!")
        else:
            print(f"Not a zip. First bytes: {data[:100]}")
            print(data[:1000].decode('utf-8', errors='replace'))
except Exception as e:
    print(f"Download error: {e}")