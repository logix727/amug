# Release Signing

AMUG stable releases use a dedicated offline RSA-4096 APK signing key.

Certificate SHA-256:
`E6:27:81:74:94:E0:24:82:66:9F:46:60:F9:E2:CA:EF:C0:01:A1:98:75:D2:56:CD:CB:88:C7:0B:5E:46:48:29`

The keystore/passwords are not stored in Git. Required build variables are
`AMUG_KEYSTORE`, `AMUG_STORE_PASSWORD`, `AMUG_KEY_ALIAS`, and
`AMUG_KEY_PASSWORD`. Keep encrypted offline backups; losing the key prevents
future updates under package `dev.logix.amug`.

Earlier prereleases were debug-signed and cannot upgrade directly to stable.
Uninstall a prerelease once before installing `0.3.0`.
