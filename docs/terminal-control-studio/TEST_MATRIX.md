# Test matrix

Evidence captured on 2026-10-14; local checks cover the current uncommitted follow-up, while the latest completed CI run tests `90467df`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,030 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 41,556,964-byte APK produced, copied to the root test artifact, and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the strict state, host-fixture, palette-locale, editor-target, and toolbar-scroll assertions were added. |
| MarketDebug lint | PASS | Latest app-source validation: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Current changes pass whitespace validation. |
| Instrumentation / Compose UI | FAIL; current follow-up awaits CI | Run [38490916105](https://github.com/ashkansuridr-bit/terminal/actions/runs/38490916105) on `90467df`: Verify PASS. API26 Debug/Preview each failed 3/114; API36 Debug failed 3/114 and Preview 2/114. Aggregate: 445/456 passed, 11 failed, 0 skipped across those four variants. SettingsToggle failed in all variants; HostEdit failed API36 Debug; the Vim profile chip reachability assertion failed in all four and Tab reachability failed on API26 Debug/Preview. API37 Debug/Preview stopped before app tests (`adb: device offline`, emulator boot timeout); release candidates were skipped. The current local follow-up makes Settings switch semantics explicit, removes pre-focus node identity assumptions from HostEdit, and compares toolbar controls against their actual scroll viewport; a fresh CI run must verify those changes. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | Run 38490916105 exercised API26/36 and attempted API37. API37 did not reach test execution (`adb: device offline`, emulator boot timeout). API31/34 are not in this workflow. Local emulator images are incomplete and no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
