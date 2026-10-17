# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,681,533 bytes
- SHA-256: `09457c88f01e6ab5a667ffb64c7774f879ae74c4279aa4251a9d897ea9b91149`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [39057888031](https://github.com/ashkansuridr-bit/terminal/actions/runs/39057888031), testing `c85441c`. Verify passed. API26 Debug had 1/114 failure (Tab reachability); API26 Preview had 3/114 (Tab, Palette selection, HostEdit port entry); API36 Debug had 1/114 (security-toggle scroll reachability); API36 Preview had 2/114 (Palette selection and physical switch tap). Across the four completed variants, 449/456 instrumentation executions passed. API37 Debug/Preview failed before tests because the emulator stayed `adb: device offline` and boot timed out; no test reports were generated. Release candidates were skipped. The current follow-up uses the supported wide toolbar layout to verify Tab dispatch, Settings search to reach the security toggle, its accessibility click action to select OLED, and an editor scoped to the named Port field. These corrections compile locally but await runtime CI evidence; compact Tab scrolling and API36 physical coordinate mapping remain unverified.

For the local follow-up, 1,030 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and v2 debug-signature verification passed. The refreshed root APK includes the current Shell preset with the Snippets launcher in the first compact viewport. UI test-harness changes are not part of the installed APK and await fresh emulator evidence. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
