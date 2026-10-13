# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: `081d0538595bcb82c43a291dc75b3735038cf13d`
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,555,788 bytes
- SHA-256: `01e0409dae251ccfaf1656c956f86165dbef1040e14dd9261e0ad1d54e404aec`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [38452720047](https://github.com/ashkansuridr-bit/terminal/actions/runs/38452720047), testing `ab331c7`. Verify passed. API26 Debug and Preview each ran 114 tests with 3 failures; API36 Debug and Preview each ran 114 tests with 2 failures. HostList and SettingsToggle failed in all four variants; the Tab keyboard accessibility test also failed on API26. API37 Debug and Preview could not start because the emulators stayed offline and timed out before tests. The owner reports a successful manual test on a physical Android phone; this workspace has no attached device or runnable emulator for an independent replay.

For the current local source, 1,026 JVM tests passed with no failures, errors, or skips. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and debug-signature verification passed. A new instrumentation run is required to verify the latest UI-test and accessibility fixes. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
