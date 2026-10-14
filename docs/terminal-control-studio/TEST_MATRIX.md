# Test matrix

Evidence captured on 2026-10-14; local checks cover the current uncommitted follow-up, while the latest completed CI run tests `1150c68`; current local follow-up is newer and awaits CI.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,030 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 41,681,527-byte APK produced, copied to the root test artifact, SHA-256 `bca914ad9d1cd4550b251b08670edb07c02f81d8c9ffc3c378acfb314e346f10`, and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed with the current progress-checked scrolling and editor/profile tests. |
| MarketDebug lint | PASS | `:app:lintMarketDebug`: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Current local follow-up passes whitespace validation. |
| Instrumentation / Compose UI | FAIL; follow-up pending CI | Run [38496174163](https://github.com/ashkansuridr-bit/terminal/actions/runs/38496174163) on `1150c68`: Verify PASS. API26 Debug failed 1/114 (Tab reachability); Preview failed 3/114 (HostEdit offscreen-value assertion, Settings toggle scroll reachability, Tab reachability). API36 Debug and Preview each failed 1/114 (All-hosts heading reachability). Aggregate 450/456 passed, 6 failed, 0 skipped across four variants. API37 Debug/Preview failed before tests (`adb: device offline`, emulator boot timeout), no reports. Release candidates skipped. Latest local follow-up uses smaller touch-scroll steps in HostList, Settings and HostEdit, brings Tab earlier in the default Shell preset, and removes the offscreen-value-only HostEdit assertion; fresh runtime status is pending.
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
