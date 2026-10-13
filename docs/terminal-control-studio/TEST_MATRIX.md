# Test matrix

Evidence captured on 2026-10-13; local application-source checks cover the current uncommitted change set, while the latest completed CI run tests `ab331c7`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,026 tests across 119 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 41,555,788-byte APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the strict state, host-fixture, palette-locale, editor-target, and toolbar-scroll assertions were added. |
| MarketDebug lint | PASS | Latest app-source validation: 0 errors, 106 warnings, 2 hints. |
| `git diff --check` | PASS | Current changes pass whitespace validation. |
| Instrumentation / Compose UI | FAIL; current follow-up awaits CI | Run [38452720047](https://github.com/ashkansuridr-bit/terminal/actions/runs/38452720047) on `ab331c7`: API26 Debug/Preview each failed 3/114 (HostList, SettingsToggle, TerminalKeyboard Tab); API36 Debug/Preview each failed 2/114 (HostList, SettingsToggle). API36 HostEdit and palette tests passed in this run. New follow-up is locally compiled but has no runtime evidence yet. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | Run 38452720047 exercised API26/36 and attempted API37. API37 did not reach test execution (`adb: device offline`, emulator boot timeout). API31/34 are not in this workflow. Local emulator images are incomplete and no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
