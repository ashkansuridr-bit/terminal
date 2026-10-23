# Android test build — MarketDebug

The APK in the repository root is a review artifact built with `:app:assembleMarketDebug`. It is intended for installation and testing only.

- Variant: `MarketDebug`
- Application source commit: recorded in the delivery commit that includes this APK
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug`
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 43,752,062 bytes
- SHA-256: `007afbb8b9146401c747cf7d79aa8bfe9fb063a54572ca75636f35789153519b`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed with APK Signature Scheme v2.

This artifact is not signed with the publisher's production key and is not a store-ready release. The verifier confirms package signature integrity only; it does not prove runtime behavior on every Android version.

The latest completed CI run is [39059627211](https://github.com/ashkansuridr-bit/terminal/actions/runs/39059627211), testing `377c644`. Verify passed. API26 Debug had 1/114 failure (Settings search); API26 Preview had 2/114 (Settings search and a stale HostEdit field); API36 Debug had 3/114 (Settings search, stale HostEdit field, and wide-layout test geometry); API36 Preview had 2/114 (Settings search and wide-layout test geometry). Across the four completed variants, 448/456 instrumentation executions passed. Palette selection and compact Snippets passed in all four variants. API37 Debug/Preview failed before tests because the emulator stayed `adb: device offline` and boot timed out; no test reports were generated. Release candidates were skipped. The current follow-up sends text through the named field's accessibility edit action, reacquires HostEdit fields after recomposition, and requests a genuinely wide portrait viewport. These corrections compile locally but await runtime CI evidence; compact Tab scrolling and API36 physical coordinate mapping remain unverified.

For the local TCS-11 follow-up, 1,031 JVM tests passed with no failures, errors, or skips. Lint reported 0 errors, 108 warnings, and 2 hints. MarketDebug app and AndroidTest Kotlin compilation, lint, APK assembly, and v2 debug-signature verification passed. The refreshed root APK includes the macro input validation and accessible macro field names. A new UI regression covers macro creation but has not run on a device; current test-harness changes still await fresh emulator evidence. See the [test matrix](docs/terminal-control-studio/TEST_MATRIX.md) and [APK verification record](docs/terminal-control-studio/APK_VERIFICATION.md).
