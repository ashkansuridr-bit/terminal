# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 43,749,858 bytes
- SHA-256: `bdcb2d74138d09a503d65428bfed1e7a91e18d472cf843de6b3ff844a2ac8aed`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [38457441726](https://github.com/ashkansuridr-bit/terminal/actions/runs/38457441726), testing `cb40830`. Verify passed. API26 Debug/Preview each ran 114 tests with 4 failures; API36 Debug ran 114 with 4 failures and Preview with 2. Across four completed variants, 442/456 instrumentation executions passed. API37 Debug/Preview timed out before tests because the emulators remained offline; release candidates were skipped. The owner reports a successful manual test on a physical Android phone; this workspace has no attached device or runnable emulator for an independent replay.

For the current local source, 1,030 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and debug-signature verification passed. Run 38457441726 completed with the failures listed above; the local HostList, HostEdit, SettingsToggle, and toolbar-scroll follow-up is awaiting a fresh CI run. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
