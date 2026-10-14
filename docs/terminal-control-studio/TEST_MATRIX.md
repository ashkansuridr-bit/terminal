# Test matrix

Evidence captured on 2026-10-14; local checks cover the current uncommitted follow-up, while the latest completed CI run tests `cb40830`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,030 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 43,749,858-byte APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the strict state, host-fixture, palette-locale, editor-target, and toolbar-scroll assertions were added. |
| MarketDebug lint | PASS | Latest app-source validation: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Current changes pass whitespace validation. |
| Instrumentation / Compose UI | FAIL; current follow-up awaits CI | Run [38457441726](https://github.com/ashkansuridr-bit/terminal/actions/runs/38457441726) on `cb40830`: Verify PASS. API26 Debug/Preview each failed 4/114; API36 Debug failed 4/114 and Preview 2/114. Aggregate: 442/456 passed, 14 failed, 0 skipped across those four variants. HostList and SettingsToggle failed in all; HostEdit failed API26 Debug/Preview and API36 Debug; TerminalKeyboard failed API26 Debug (Tab) and API36 Debug (Vim profile chip). API37 Debug/Preview timed out before test execution; release candidates skipped. The current uncommitted follow-up moves the list swipe away from the floating button, reacquires post-IME field geometry, preserves Switch semantics, and swipes through toolbar/profile LazyRows; fresh CI is required. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | Run 38457441726 exercised API26/36 and attempted API37. API37 did not reach test execution (`adb: device offline`, emulator boot timeout). API31/34 are not in this workflow. Local emulator images are incomplete and no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
