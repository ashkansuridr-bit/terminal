# Test matrix

Evidence updated on 2026-10-23; latest completed CI tests `377c644`. Current local UI-test corrections are newer and await CI.

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
| Instrumentation / Compose UI | FAIL; targeted follow-up pending CI | Run [39059627211](https://github.com/ashkansuridr-bit/terminal/actions/runs/39059627211) on `377c644`: Verify PASS; API26/36 Debug/Preview 448 passed, 8 failed, 0 skipped across 456 tests. API26 Debug: Settings search (1); API26 Preview: Settings search and stale HostEdit field (2); API36 Debug: Settings search, stale HostEdit field and wide-layout test geometry (3); API36 Preview: Settings search and wide-layout test geometry (2). Palette selection and compact Snippets passed in all four variants. API37 Debug/Preview failed before tests (`adb: device offline`, boot timeout); release candidates skipped. Current targeted test corrections compile locally; device runtime remains unverified. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
