# Test matrix

Evidence updated on 2026-10-23; latest completed CI tests `377c644`. Current local UI-test corrections are newer and await CI.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Current TCS-11 run: 1,031 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. Invalid macro replacement and shared control-character predicate regressions pass. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 43,752,062-byte APK produced, copied to the root test artifact, SHA-256 `007afbb8b9146401c747cf7d79aa8bfe9fb063a54572ca75636f35789153519b`, and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug` on current TCS-11 source; 44,863,164-byte APK produced and Android Debug v2 certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | MarketPreview AndroidTest Kotlin compilation passed with the new macro creation regression. Preview APKs and GPlayPreview were not rebuilt for this follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | MarketDebug and MarketPreview AndroidTest Kotlin compilation passed with the new macro creation regression. Device runtime is unverified. |
| MarketDebug lint | PASS | `:app:lintMarketDebug`: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Current local follow-up passes whitespace validation. |
| Instrumentation / Compose UI | FAIL; targeted follow-up pending CI | Run [39059627211](https://github.com/ashkansuridr-bit/terminal/actions/runs/39059627211) on `377c644`: Verify PASS; API26/36 Debug/Preview 448 passed, 8 failed, 0 skipped across 456 tests. API26 Debug: Settings search (1); API26 Preview: Settings search and stale HostEdit field (2); API36 Debug: Settings search, stale HostEdit field and wide-layout test geometry (3); API36 Preview: Settings search and wide-layout test geometry (2). Palette selection and compact Snippets passed in all four variants. API37 Debug/Preview failed before tests (`adb: device offline`, boot timeout); release candidates skipped. Current targeted test corrections compile locally; device runtime remains unverified. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
