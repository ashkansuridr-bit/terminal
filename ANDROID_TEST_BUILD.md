# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,556,964 bytes
- SHA-256: `056f67ef0d33c82307c857287768418c03a42c174ab1cb0899e6660a2d2c4680`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [38492997313](https://github.com/ashkansuridr-bit/terminal/actions/runs/38492997313), testing `5bfbed7`. Verify passed. API26 Debug/Preview each ran 114 tests with 3 failures; API36 Debug ran 114 with 2 failures and Preview with 1. Across four completed variants, 447/456 instrumentation executions passed. API37 Debug/Preview remained offline before producing reports; release candidates were skipped. The owner reports a successful manual test on a physical Android phone; this workspace has no attached device or runnable emulator for an independent replay.

For the local follow-up, 1,030 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and v2 debug-signature verification passed. The refreshed root APK includes the explicit settings switch semantics and a wrapping profile selector for narrow screens. New scroll-progress and editor-flow test fixes compile locally and await fresh emulator evidence. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
