# Publisher handoff — Terminal SSH 0.1.0

Release identity:
- Application ID / namespace: `app.terminalssh.secure`
- Version name: `0.1.0`
- Version code: `1`
- Minimum Android: API 26
- Target / compile SDK: API 36

Binary release requires a publisher-owned Android signing key. Keep the keystore and all passwords outside the repository. Export `TERMINAL_KEYSTORE_PATH`, `TERMINAL_KEYSTORE_PASSWORD`, `TERMINAL_KEY_ALIAS`, and `TERMINAL_KEY_PASSWORD`, then run `./scripts/release_local.sh` on a machine with JDK 17+, Android SDK 36, Android SDK Build-Tools, and Gradle 8.13.

Before market upload, install the signed APK on real/emulated API 26 and API 36 devices. Validate first-use fingerprint approval against a controlled SSH server, reconnect with the accepted key, then replace the server host key and confirm the client blocks the connection. Confirm terminal input/output, resize, copy/paste, rotation/background lifecycle, and network loss/reconnect.

Use the files under `store/` for the Persian Bazaar listing and privacy text. Capture screenshots only from the actual signed build that passed the checks above.
