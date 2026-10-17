# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,681,527 bytes
- SHA-256: `bca914ad9d1cd4550b251b08670edb07c02f81d8c9ffc3c378acfb314e346f10`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [38500330075](https://github.com/ashkansuridr-bit/terminal/actions/runs/38500330075), testing `674ecc4`. Verify passed. API26 Debug and Preview each failed 1/114 on the Snippets toolbar-key reachability helper. API36 Debug and Preview each failed 2/114: the same Snippets helper and the OLED palette visibility assertion. Across the four completed variants, 451/456 instrumentation executions passed. The Settings security-toggle, Tab reachability, HostEdit validation and HostList grouping regressions passed in this run. API37 Debug/Preview failed before tests because the emulator stayed `adb: device offline` and boot timed out; no test reports were generated. Release candidates were skipped. The owner reports a successful manual test on a physical Android phone; this workspace has no attached device or runnable emulator for an independent replay. The current follow-up adds bounded toolbar diagnostics and translates horizontal palette gestures across API 36 inset coordinates; runtime verification is pending.

For the local follow-up, 1,030 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and v2 debug-signature verification passed. The refreshed root APK includes the accessible settings switch semantics, wrapping profile selectors for narrow screens, and Tab prioritized in the Shell preset. The newest small-step Snippets and longer Settings scroll helpers compile locally and await fresh emulator evidence. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
