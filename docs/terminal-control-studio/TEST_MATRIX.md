# Test matrix

Evidence captured on 2026-10-17; latest completed CI tests `c85441c`. Current local UI-test corrections are newer and await CI.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,030 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 41,681,533-byte APK produced, copied to the root test artifact, SHA-256 `09457c88f01e6ab5a667ffb64c7774f879ae74c4279aa4251a9d897ea9b91149`, and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed with the current progress-checked scrolling and editor/profile tests. |
| MarketDebug lint | PASS | `:app:lintMarketDebug`: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Current local follow-up passes whitespace validation. |
| Instrumentation / Compose UI | FAIL; targeted follow-up pending CI | Run [39057888031](https://github.com/ashkansuridr-bit/terminal/actions/runs/39057888031) on `c85441c`: Verify PASS; API26/36 Debug/Preview 449 passed, 7 failed, 0 skipped across 456 tests. Failures: API26 Debug Tab; API26 Preview Tab, Palette, HostEdit Port; API36 Debug security-toggle scroll; API36 Preview Palette and physical toggle tap. API37 Debug/Preview failed before tests (`adb: device offline`, boot timeout); release candidates skipped. Current local corrections compile; compact Tab scrolling and API36 physical coordinate mapping remain unverified. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
