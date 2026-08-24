# Release status — 0.1.0

Source/configuration gates are complete for `app.terminalssh.secure`: security audit, market metadata audit, known-host policy smoke tests, AES-GCM vault smoke tests, XML validation, signing externalization, privacy/listing material, and CI release workflow.

Binary verification gate completed on 2026-08-13 on the build server and in GitHub Actions (run `31667494927`, branch `main`, commit `c47d119`):

- `testReleaseUnitTest` — PASS
- `lintRelease` — PASS (0 errors)
- `assembleDebug` — PASS (installable debug APK produced)
- `assembleRelease` — PASS (unsigned APK produced)
- `bundleRelease` — PASS (unsigned AAB produced)
- SSH policy and AES-GCM vault JVM smoke tests — PASS
- GitHub Actions `verify` job — green; debug APK / unsigned release APK / AAB / lint report uploaded as artifact `terminal-0.1.0-verify-<sha>`.

Signed-market gate (`signed-market-release`) remains skipped: it requires the publisher-owned signing identity (`TERMINAL_KEYSTORE_BASE64`, `TERMINAL_KEYSTORE_PASSWORD`, `TERMINAL_KEY_ALIAS`, `TERMINAL_KEY_PASSWORD` secrets) and only runs on `v*` tags. A market-ready APK/AAB is only produced after that gate passes with the real signing identity and device/emulator SSH validation on API 26+.
