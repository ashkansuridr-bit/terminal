# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,556,964 bytes
- SHA-256: `26d66df744045e47537894bbee05507124803cb95ba99498007f806798fb03a0`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [38490916105](https://github.com/ashkansuridr-bit/terminal/actions/runs/38490916105), testing `90467df`. Verify passed. API26 Debug/Preview each ran 114 tests with 3 failures; API36 Debug ran 114 with 3 failures and Preview with 2. Across four completed variants, 445/456 instrumentation executions passed. API37 Debug/Preview stopped before tests because `adb` reported the device offline and the emulator boot timed out; release candidates were skipped. The owner reports a successful manual test on a physical Android phone; this workspace has no attached device or runnable emulator for an independent replay.

For the current local source, 1,030 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and v2 debug-signature verification passed. The refreshed root APK includes the explicit semantics for the labeled settings switch. The latest local HostEdit and toolbar test changes, plus this switch fix, await a fresh CI runtime. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
